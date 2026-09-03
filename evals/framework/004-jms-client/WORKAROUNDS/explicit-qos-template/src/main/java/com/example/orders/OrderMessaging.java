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

    private static final int URGENT_PRIORITY = 9;
    private static final long URGENT_TTL_MILLIS = 5 * 60 * 1000L;
    private static final long CONFIRMATION_TIMEOUT_MILLIS = 5_000L;

    private final JmsTemplate jmsTemplate;

    public OrderMessaging(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
        this.jmsTemplate.setExplicitQosEnabled(true);
        this.jmsTemplate.setReceiveTimeout(CONFIRMATION_TIMEOUT_MILLIS);
    }

    public void publishOrderEvent(Order order) {
        if (order.urgent()) {
            jmsTemplate.setPriority(URGENT_PRIORITY);
            jmsTemplate.setTimeToLive(URGENT_TTL_MILLIS);
            jmsTemplate.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
            jmsTemplate.convertAndSend(EVENTS_QUEUE, eventPayload(order));
            jmsTemplate.setPriority(Message.DEFAULT_PRIORITY);
            jmsTemplate.setTimeToLive(Message.DEFAULT_TIME_TO_LIVE);
            jmsTemplate.setDeliveryMode(Message.DEFAULT_DELIVERY_MODE);
        }
        else {
            jmsTemplate.convertAndSend(EVENTS_QUEUE, eventPayload(order));
        }
    }

    public String requestConfirmation(Order order) {
        Message reply = jmsTemplate.sendAndReceive(CONFIRMATIONS_QUEUE,
                session -> session.createTextMessage(order.id()));
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
