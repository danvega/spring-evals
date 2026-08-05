package com.example.orders;

import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;

@Component
public class ConfirmationProcessor {

    @JmsListener(destination = OrderMessaging.CONFIRMATIONS_QUEUE)
    @SendTo(OrderMessaging.CONFIRMATIONS_REPLY_QUEUE)
    public String confirm(String orderId) {
        return "CONFIRMED-" + orderId;
    }
}
