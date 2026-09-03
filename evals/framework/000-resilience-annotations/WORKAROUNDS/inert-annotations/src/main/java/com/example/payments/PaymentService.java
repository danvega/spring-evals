package com.example.payments;

import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Carries the annotation but nothing switches the mechanism on, so the
 * retry is done by hand.
 */
@Service
public class PaymentService {

    private static final int MAX_RETRIES = 3;

    private final PaymentGateway paymentGateway;

    public PaymentService(PaymentGateway paymentGateway) {
        this.paymentGateway = paymentGateway;
    }

    @Retryable(includes = TransientGatewayException.class, maxRetries = MAX_RETRIES, delay = 50)
    public String charge(String orderId) {
        for (int retries = 0; ; retries++) {
            try {
                return paymentGateway.charge(orderId);
            } catch (TransientGatewayException e) {
                if (retries >= MAX_RETRIES) {
                    throw e;
                }
                pause();
            }
        }
    }

    private static void pause() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting to retry", e);
        }
    }
}
