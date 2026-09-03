package com.example.payments;

import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

/** Class-level placement: every public method retries transient gateway failures. */
@Service
@Retryable(includes = TransientGatewayException.class, maxRetries = 3, delay = 50)
public class PaymentService {

    private final PaymentGateway paymentGateway;

    public PaymentService(PaymentGateway paymentGateway) {
        this.paymentGateway = paymentGateway;
    }

    public String charge(String orderId) {
        return paymentGateway.charge(orderId);
    }
}
