package com.example.payments;

import java.lang.reflect.Method;

import org.springframework.resilience.retry.MethodRetryPredicate;

/** Retry only when the provider dropped the connection; anything else is a real error. */
public class TransientFailure implements MethodRetryPredicate {

    @Override
    public boolean shouldRetry(Method method, Throwable throwable) {
        return throwable instanceof TransientGatewayException;
    }
}
