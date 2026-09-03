package com.example.payments;

import java.time.Duration;

import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final PaymentGateway paymentGateway;

    private final RetryTemplate retries = new RetryTemplate(RetryPolicy.builder()
            .includes(TransientGatewayException.class)
            .maxRetries(3)
            .delay(Duration.ofMillis(50))
            .build());

    public PaymentService(PaymentGateway paymentGateway) {
        this.paymentGateway = paymentGateway;
    }

    public String charge(String orderId) {
        try {
            return retries.execute(() -> paymentGateway.charge(orderId));
        } catch (RetryException e) {
            throw new IllegalStateException("payment provider unavailable for order " + orderId, e);
        }
    }
}
