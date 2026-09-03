package com.example.payments;

import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final PaymentGateway paymentGateway;

    public PaymentService(PaymentGateway paymentGateway) {
        this.paymentGateway = paymentGateway;
    }

    /**
     * The gateway drops roughly the first two connection attempts, so allow
     * up to 3 retries after the initial call. Proxy-based: external calls
     * through the bean are retried, self-invocations are not.
     */
    @Retryable(includes = TransientGatewayException.class, maxRetries = 3, delay = 50)
    public String charge(String orderId) {
        return paymentGateway.charge(orderId);
    }
}
