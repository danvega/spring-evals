package com.example.orders;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSException;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;

import org.springframework.jms.core.JmsClient;
import org.springframework.stereotype.Service;

@Service
public class OrderMessaging {

    static final String EVENTS_QUEUE = "orders.events";
    static final String CONFIRMATIONS_QUEUE = "orders.confirmations";

    private static final int URGENT_PRIORITY = 9;
    private static final long URGENT_TTL_MILLIS = 5 * 60 * 1000L;
    private static final long CONFIRMATION_TIMEOUT_MILLIS = 5_000L;

    private final JmsClient jmsClient;
    private final ConnectionFactory connectionFactory;

    public OrderMessaging(JmsClient jmsClient, ConnectionFactory connectionFactory) {
        this.jmsClient = jmsClient;
        this.connectionFactory = connectionFactory;
    }

    public void publishOrderEvent(Order order) {
        if (order.urgent()) {
            sendUrgent(eventPayload(order));
            return;
        }
        jmsClient.destination(EVENTS_QUEUE).send(eventPayload(order));
    }

    private void sendUrgent(String payload) {
        try (Connection connection = connectionFactory.createConnection();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                MessageProducer producer = session.createProducer(session.createQueue(EVENTS_QUEUE))) {
            producer.send(session.createTextMessage(payload), DeliveryMode.NON_PERSISTENT, URGENT_PRIORITY,
                    URGENT_TTL_MILLIS);
        }
        catch (JMSException ex) {
            throw new IllegalStateException("could not publish urgent order event", ex);
        }
    }

    public String requestConfirmation(Order order) {
        return jmsClient.destination(CONFIRMATIONS_QUEUE)
                .withReceiveTimeout(CONFIRMATION_TIMEOUT_MILLIS)
                .sendAndReceive(order.id(), String.class)
                .orElse("NO-CONFIRMATION");
    }

    private String eventPayload(Order order) {
        return "ORDER-EVENT|" + order.id() + "|" + order.item() + "|" + order.quantity()
                + "|urgent=" + order.urgent();
    }
}
