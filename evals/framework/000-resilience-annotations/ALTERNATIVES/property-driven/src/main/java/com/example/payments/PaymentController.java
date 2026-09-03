package com.example.payments;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;
    private final ReportService reportService;
    private final ReportGenerator reportGenerator;

    public PaymentController(PaymentService paymentService, PaymentGateway paymentGateway,
            ReportService reportService, ReportGenerator reportGenerator) {
        this.paymentService = paymentService;
        this.paymentGateway = paymentGateway;
        this.reportService = reportService;
        this.reportGenerator = reportGenerator;
    }

    @PostMapping("/payments/{orderId}/charge")
    public Map<String, Object> charge(@PathVariable String orderId) {
        String receipt = paymentService.charge(orderId);
        return Map.of("receipt", receipt, "attempts", paymentGateway.attemptsFor(orderId));
    }

    @PostMapping("/reports/run")
    public Map<String, Object> runReport() {
        return Map.of("report", reportService.generate());
    }

    @GetMapping("/reports/stats")
    public Map<String, Object> reportStats() {
        return Map.of("maxConcurrent", reportGenerator.maxObservedConcurrency());
    }
}
