package com.example.orders;

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

    public OrderMessaging(JmsClient jmsClient) {
        this.jmsClient = jmsClient;
    }

    public void publishOrderEvent(Order order) {
        if (order.urgent()) {
            // Urgent events jump the queue, expire after five minutes and
            // do not need to survive a broker restart.
            jmsClient.destination(EVENTS_QUEUE)
                    .withPriority(URGENT_PRIORITY)
                    .withTimeToLive(URGENT_TTL_MILLIS)
                    .withDeliveryPersistent(false)
                    .send(eventPayload(order));
            return;
        }
        jmsClient.destination(EVENTS_QUEUE).send(eventPayload(order));
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
