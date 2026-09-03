package com.example.payments;

import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Service;

/** Class-level placement: at most 2 concurrent executions across the bean's public methods. */
@Service
@ConcurrencyLimit(2)
public class ReportService {

    private final ReportGenerator reportGenerator;

    public ReportService(ReportGenerator reportGenerator) {
        this.reportGenerator = reportGenerator;
    }

    public String generate() {
        return reportGenerator.generate();
    }
}
