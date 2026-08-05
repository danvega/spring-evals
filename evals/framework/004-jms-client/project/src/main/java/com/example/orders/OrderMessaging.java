package com.example.orders;

import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;

import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderMessaging {

    static final String EVENTS_QUEUE = "orders.events";
    static final String CONFIRMATIONS_QUEUE = "orders.confirmations";
    static final String CONFIRMATIONS_REPLY_QUEUE = "orders.confirmations.reply";

    private static final int URGENT_PRIORITY = 9;
    private static final long URGENT_TTL_MILLIS = 5 * 60 * 1000L;
    private static final long CONFIRMATION_TIMEOUT_MILLIS = 5_000L;

    private final JmsTemplate jmsTemplate;

    public OrderMessaging(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    public void publishOrderEvent(Order order) {
        if (order.urgent()) {
            // Urgent events jump the queue, expire after five minutes and
            // do not need to survive a broker restart.
            jmsTemplate.setPriority(URGENT_PRIORITY);
            jmsTemplate.setTimeToLive(URGENT_TTL_MILLIS);
            jmsTemplate.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
            jmsTemplate.send(EVENTS_QUEUE, session -> {
                TextMessage message = session.createTextMessage(eventPayload(order));
                return message;
            });
            jmsTemplate.setPriority(Message.DEFAULT_PRIORITY);
            jmsTemplate.setTimeToLive(Message.DEFAULT_TIME_TO_LIVE);
            jmsTemplate.setDeliveryMode(Message.DEFAULT_DELIVERY_MODE);
        }
        else {
            jmsTemplate.convertAndSend(EVENTS_QUEUE, eventPayload(order));
        }
    }

    public String requestConfirmation(Order order) {
        jmsTemplate.convertAndSend(CONFIRMATIONS_QUEUE, order.id());
        jmsTemplate.setReceiveTimeout(CONFIRMATION_TIMEOUT_MILLIS);
        Message reply = jmsTemplate.receive(CONFIRMATIONS_REPLY_QUEUE);
        if (reply == null) {
            return "NO-CONFIRMATION";
        }
        try {
            return ((TextMessage) reply).getText();
        }
        catch (JMSException ex) {
            throw new IllegalStateException("could not read confirmation reply", ex);
        }
    }

    private String eventPayload(Order order) {
        return "ORDER-EVENT|" + order.id() + "|" + order.item() + "|" + order.quantity()
                + "|urgent=" + order.urgent();
    }
}
