package com.example.orders;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.DeliveryMode;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TemporaryQueue;
import jakarta.jms.TextMessage;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jms.config.JmsListenerEndpointRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hidden eval assertions. Orders are placed through the public REST API and
 * the resulting messages are read back with raw JMS consumers, so the headers
 * observed here are exactly what the broker delivered.
 *
 * The application's JMS listeners start disabled. The first test stands in
 * for the confirmation processor so it can inspect the request the service
 * sends; every later test starts the listeners before placing an order.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jms.listener.auto-startup=false")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderMessagingEvalTest {

    private static final String EVENTS_QUEUE = "orders.events";
    private static final String CONFIRMATIONS_QUEUE = "orders.confirmations";
    private static final long FIVE_MINUTES_MILLIS = 5 * 60 * 1000L;

    @Autowired
    Environment environment;

    @Autowired
    ConnectionFactory connectionFactory;

    @Autowired
    JmsListenerEndpointRegistry listenerRegistry;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpRequest orderRequest(String id, String item, int quantity, boolean urgent) {
        String body = """
                {"id":"%s","item":"%s","quantity":%d,"urgent":%s}""".formatted(id, item, quantity, urgent);
        return HttpRequest.newBuilder(
                URI.create("http://localhost:" + environment.getProperty("local.server.port") + "/api/orders"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private HttpResponse<String> placeOrder(String id, String item, int quantity, boolean urgent) throws Exception {
        return http.send(orderRequest(id, item, quantity, urgent), HttpResponse.BodyHandlers.ofString());
    }

    /** Starting an already running registry is a no-op, so every test that needs the listeners calls this. */
    private void startConfirmationListeners() {
        listenerRegistry.start();
    }

    /** Drains the events queue until the event for the given order id shows up. */
    private Message receiveEventFor(String orderId) throws JMSException {
        try (Connection connection = connectionFactory.createConnection()) {
            connection.start();
            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                    MessageConsumer consumer = session.createConsumer(session.createQueue(EVENTS_QUEUE))) {
                long deadline = System.currentTimeMillis() + 10_000;
                while (System.currentTimeMillis() < deadline) {
                    Message message = consumer.receive(2_000);
                    if (message instanceof TextMessage text && text.getText().contains(orderId)) {
                        return message;
                    }
                }
            }
        }
        throw new AssertionError("no order event for " + orderId + " arrived on " + EVENTS_QUEUE
                + " within 10 seconds");
    }

    @Test
    @Order(1)
    void confirmationRequestCarriesItsOwnReplyDestination() throws Exception {
        CompletableFuture<HttpResponse<String>> pending;

        try (Connection connection = connectionFactory.createConnection()) {
            connection.start();
            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                    MessageConsumer consumer = session.createConsumer(session.createQueue(CONFIRMATIONS_QUEUE))) {
                pending = http.sendAsync(orderRequest("EVT-REPLY-5", "warp core", 2, false),
                        HttpResponse.BodyHandlers.ofString());
                Message request = consumer.receive(10_000);
                assertThat(request)
                        .as("placing an order must put a confirmation request on %s", CONFIRMATIONS_QUEUE)
                        .isNotNull();

                Destination replyTo = request.getJMSReplyTo();
                assertThat(replyTo)
                        .as("the confirmation request must carry its own reply destination; "
                                + "a reply can otherwise only travel over a shared fixed queue")
                        .isNotNull();
                assertThat(replyTo)
                        .as("the reply destination must belong to this request alone, not be a shared queue")
                        .isInstanceOf(TemporaryQueue.class);

                TextMessage reply = session.createTextMessage("CONFIRMED-EVT-REPLY-5");
                if (request.getJMSCorrelationID() != null) {
                    reply.setJMSCorrelationID(request.getJMSCorrelationID());
                }
                try (MessageProducer producer = session.createProducer(replyTo)) {
                    producer.send(reply);
                }
            }
        }

        HttpResponse<String> response = pending.get(20, TimeUnit.SECONDS);
        assertThat(response.statusCode()).as("POST /api/orders must keep working").isEqualTo(200);
        assertThat(response.body())
                .as("the reply sent to the request's own reply destination must answer the waiting request")
                .contains("EVT-REPLY-5")
                .contains("CONFIRMED-EVT-REPLY-5");
    }

    @Test
    void urgentOrderEventCarriesQosOnTheBroker() throws Exception {
        startConfirmationListeners();
        HttpResponse<String> response = placeOrder("EVT-URGENT-77", "flux capacitor", 1, true);
        assertThat(response.statusCode()).as("POST /api/orders must keep working").isEqualTo(200);

        Message event = receiveEventFor("EVT-URGENT-77");

        assertThat(event.getJMSPriority())
                .as("urgent order events must reach the broker with priority 9; "
                        + "the broker delivered priority %s, so the setting was dropped at send time",
                        event.getJMSPriority())
                .isEqualTo(9);
        assertThat(event.getJMSExpiration())
                .as("urgent order events must carry a real expiration from a five minute time to live; "
                        + "0 means no time to live was applied")
                .isGreaterThan(0);
        assertThat(event.getJMSExpiration() - System.currentTimeMillis())
                .as("the remaining time to live must be about five minutes, not a made up value")
                .isBetween(FIVE_MINUTES_MILLIS - 120_000, FIVE_MINUTES_MILLIS + 60_000);
        assertThat(event.getJMSDeliveryMode())
                .as("urgent order events must be sent non-persistent")
                .isEqualTo(DeliveryMode.NON_PERSISTENT);
    }

    @Test
    void normalOrderEventKeepsBrokerDefaults() throws Exception {
        startConfirmationListeners();
        HttpResponse<String> response = placeOrder("EVT-NORMAL-42", "paper clips", 500, false);
        assertThat(response.statusCode()).as("POST /api/orders must keep working").isEqualTo(200);

        Message event = receiveEventFor("EVT-NORMAL-42");

        assertThat(event.getJMSPriority())
                .as("normal order events must keep the default priority, not the urgent one")
                .isEqualTo(Message.DEFAULT_PRIORITY);
        assertThat(event.getJMSExpiration())
                .as("normal order events must not expire")
                .isEqualTo(0);
        assertThat(event.getJMSDeliveryMode())
                .as("normal order events must stay persistent")
                .isEqualTo(DeliveryMode.PERSISTENT);
    }

    @Test
    void confirmationRoundTripAnswersSynchronously() throws Exception {
        startConfirmationListeners();
        HttpResponse<String> response = placeOrder("EVT-CONFIRM-11", "rubber duck", 3, false);

        assertThat(response.statusCode()).as("POST /api/orders must keep working").isEqualTo(200);
        assertThat(response.body())
                .as("the response must carry the confirmation produced over the confirmations queue")
                .contains("EVT-CONFIRM-11")
                .contains("CONFIRMED-EVT-CONFIRM-11");
    }
}
