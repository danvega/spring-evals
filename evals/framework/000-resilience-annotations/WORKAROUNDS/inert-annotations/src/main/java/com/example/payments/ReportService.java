package com.example.payments;

import java.util.concurrent.locks.ReentrantLock;

import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Service;

/**
 * Carries the annotation but nothing switches the mechanism on, so the cap
 * is a lock. One generation at a time stays under the limit of 2.
 */
@Service
public class ReportService {

    private final ReportGenerator reportGenerator;
    private final ReentrantLock slot = new ReentrantLock();

    public ReportService(ReportGenerator reportGenerator) {
        this.reportGenerator = reportGenerator;
    }

    @ConcurrencyLimit(2)
    public String generate() {
        slot.lock();
        try {
            return reportGenerator.generate();
        } finally {
            slot.unlock();
        }
    }
}
