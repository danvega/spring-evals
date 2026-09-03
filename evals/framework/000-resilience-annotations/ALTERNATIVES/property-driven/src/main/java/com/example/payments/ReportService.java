package com.example.payments;

import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    private final ReportGenerator reportGenerator;

    public ReportService(ReportGenerator reportGenerator) {
        this.reportGenerator = reportGenerator;
    }

    @ConcurrencyLimit(limitString = "${reports.concurrency-limit}")
    public String generate() {
        return reportGenerator.generate();
    }
}
