package com.example.orders;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderMessaging messaging;

    public OrderController(OrderMessaging messaging) {
        this.messaging = messaging;
    }

    @PostMapping
    public OrderReceipt place(@RequestBody Order order) {
        messaging.publishOrderEvent(order);
        String status = messaging.requestConfirmation(order);
        return new OrderReceipt(order.id(), status);
    }
}
