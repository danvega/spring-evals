package com.example.payments;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * Generates settlement reports. Generation is memory-hungry; the box falls
 * over when too many run at once. Tracks observed concurrency so operations
 * can graph it. Stands in for the real generator: DO NOT MODIFY.
 */
@Component
public class ReportGenerator {

    private final AtomicInteger current = new AtomicInteger();
    private final AtomicInteger maxObserved = new AtomicInteger();

    public String generate() {
        int now = current.incrementAndGet();
        maxObserved.accumulateAndGet(now, Math::max);
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while generating report", e);
        } finally {
            current.decrementAndGet();
        }
        return "settlement-report";
    }

    public int maxObservedConcurrency() {
        return maxObserved.get();
    }
}
