package com.example.payments;

import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final PaymentGateway paymentGateway;

    public PaymentService(PaymentGateway paymentGateway) {
        this.paymentGateway = paymentGateway;
    }

    @GatewayRetry
    public String charge(String orderId) {
        return paymentGateway.charge(orderId);
    }
}
