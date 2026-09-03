package com.example.payments;

import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final PaymentGateway paymentGateway;

    public PaymentService(PaymentGateway paymentGateway) {
        this.paymentGateway = paymentGateway;
    }

    @Retryable(predicate = TransientFailure.class,
            maxRetriesString = "${payments.gateway.max-retries}",
            delayString = "${payments.gateway.delay}")
    public String charge(String orderId) {
        return paymentGateway.charge(orderId);
    }
}
