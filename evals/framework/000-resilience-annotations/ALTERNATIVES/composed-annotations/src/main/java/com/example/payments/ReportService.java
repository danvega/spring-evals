package com.example.payments;

import org.springframework.stereotype.Service;

@Service
public class ReportService {

    private final ReportGenerator reportGenerator;

    public ReportService(ReportGenerator reportGenerator) {
        this.reportGenerator = reportGenerator;
    }

    @ReportSlot
    public String generate() {
        return reportGenerator.generate();
    }
}
