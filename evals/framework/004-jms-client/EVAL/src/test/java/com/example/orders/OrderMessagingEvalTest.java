package com.example.orders;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hidden eval assertions. Orders are placed through the public REST API and
 * the resulting event is read back with a raw JMS consumer, so the QoS
 * headers observed here are exactly what the broker delivered.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderMessagingEvalTest {

    private static final String EVENTS_QUEUE = "orders.events";
    private static final long FIVE_MINUTES_MILLIS = 5 * 60 * 1000L;

    @Autowired
    Environment environment;

    @Autowired
    ConnectionFactory connectionFactory;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> placeOrder(String id, String item, int quantity, boolean urgent) throws Exception {
        String body = """
                {"id":"%s","item":"%s","quantity":%d,"urgent":%s}""".formatted(id, item, quantity, urgent);
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + environment.getProperty("local.server.port") + "/api/orders"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
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
    void urgentOrderEventCarriesQosOnTheBroker() throws Exception {
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
        HttpResponse<String> response = placeOrder("EVT-CONFIRM-11", "rubber duck", 3, false);

        assertThat(response.statusCode()).as("POST /api/orders must keep working").isEqualTo(200);
        assertThat(response.body())
                .as("the response must carry the confirmation produced over the confirmations queue")
                .contains("EVT-CONFIRM-11")
                .contains("CONFIRMED-EVT-CONFIRM-11");
    }
}
