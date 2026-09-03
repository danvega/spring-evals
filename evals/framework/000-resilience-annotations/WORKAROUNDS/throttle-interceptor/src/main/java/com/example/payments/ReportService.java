package com.example.payments;

/** Registered as a bean by ReportConfig, behind a throttling proxy. */
public class ReportService {

    private final ReportGenerator reportGenerator;

    public ReportService(ReportGenerator reportGenerator) {
        this.reportGenerator = reportGenerator;
    }

    public String generate() {
        return reportGenerator.generate();
    }
}
