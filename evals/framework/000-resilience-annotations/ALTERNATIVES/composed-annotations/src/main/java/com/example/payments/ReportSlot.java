package com.example.payments;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.resilience.annotation.*;

/** The team's reusable cap for memory-hungry report work: two slots, callers wait. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ConcurrencyLimit(2)
public @interface ReportSlot {
}
