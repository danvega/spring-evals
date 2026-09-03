package com.example.payments;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * Simulates our payment provider. The provider's edge drops roughly the
 * first two connection attempts for every new order before a request gets
 * through. This class stands in for the external system: DO NOT MODIFY.
 */
@Component
public class PaymentGateway {

    private final Map<String, AtomicInteger> attemptsByOrder = new ConcurrentHashMap<>();

    public String charge(String orderId) {
        int attempt = attemptsByOrder.computeIfAbsent(orderId, id -> new AtomicInteger()).incrementAndGet();
        if (attempt < 3) {
            throw new TransientGatewayException("connection reset by payment provider (attempt " + attempt + ")");
        }
        return "PAY-" + orderId;
    }

    public int attemptsFor(String orderId) {
        AtomicInteger attempts = attemptsByOrder.get(orderId);
        return attempts == null ? 0 : attempts.get();
    }
}
