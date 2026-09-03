package com.example.payments;

/** Thrown when the payment provider drops the connection. Retrying usually succeeds. */
public class TransientGatewayException extends RuntimeException {

    public TransientGatewayException(String message) {
        super(message);
    }
}
