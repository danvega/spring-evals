package com.example.payments;

import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    private final ReportGenerator reportGenerator;

    public ReportService(ReportGenerator reportGenerator) {
        this.reportGenerator = reportGenerator;
    }

    /** At most 2 concurrent generations; additional callers block and wait. */
    @ConcurrencyLimit(2)
    public String generate() {
        return reportGenerator.generate();
    }
}
