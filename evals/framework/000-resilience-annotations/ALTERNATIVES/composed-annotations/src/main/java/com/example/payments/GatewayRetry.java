package com.example.payments;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.resilience.annotation.*;

/** The team's reusable retry policy for calls that cross the payment provider's edge. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Retryable(includes = TransientGatewayException.class, maxRetries = 3, delay = 50)
public @interface GatewayRetry {
}
