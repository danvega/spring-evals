package com.example.payments;

import java.util.concurrent.Semaphore;

import org.springframework.stereotype.Service;

@Service
public class ReportService {

    private final ReportGenerator reportGenerator;
    private final Semaphore slots = new Semaphore(2);

    public ReportService(ReportGenerator reportGenerator) {
        this.reportGenerator = reportGenerator;
    }

    public String generate() {
        slots.acquireUninterruptibly();
        try {
            return reportGenerator.generate();
        } finally {
            slots.release();
        }
    }
}
