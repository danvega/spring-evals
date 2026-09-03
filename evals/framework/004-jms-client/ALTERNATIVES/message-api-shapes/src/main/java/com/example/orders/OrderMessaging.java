package com.example.orders;

import java.util.Optional;

import org.springframework.jms.core.JmsClient;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
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
        Message<String> event = MessageBuilder.withPayload(eventPayload(order))
                .setHeader("orderId", order.id())
                .build();
        JmsClient.OperationSpec events = jmsClient.destination(EVENTS_QUEUE);
        if (order.urgent()) {
            events = events.withPriority(URGENT_PRIORITY)
                    .withTimeToLive(URGENT_TTL_MILLIS)
                    .withDeliveryPersistent(false);
        }
        events.send(event);
    }

    public String requestConfirmation(Order order) {
        Message<String> request = MessageBuilder.withPayload(order.id()).build();
        Optional<Message<?>> reply = jmsClient.destination(CONFIRMATIONS_QUEUE)
                .withReceiveTimeout(CONFIRMATION_TIMEOUT_MILLIS)
                .sendAndReceive(request);
        return reply.map(message -> String.valueOf(message.getPayload())).orElse("NO-CONFIRMATION");
    }

    private String eventPayload(Order order) {
        return "ORDER-EVENT|" + order.id() + "|" + order.item() + "|" + order.quantity()
                + "|urgent=" + order.urgent();
    }
}
