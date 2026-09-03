package com.example.fieldnotes;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DirectoryController {

    private final CustomerRepository customers;
    private final InvoiceRepository invoices;
    private final ProspectRepository prospects;

    public DirectoryController(CustomerRepository customers, InvoiceRepository invoices,
            ProspectRepository prospects) {
        this.customers = customers;
        this.invoices = invoices;
        this.prospects = prospects;
    }

    @GetMapping("/customers/active")
    public List<Customer> activeCustomers() {
        return customers.findByActiveTrue();
    }

    @GetMapping("/invoices/overdue")
    public List<Invoice> overdueInvoices() {
        return invoices.findByPaidFalseAndDueDateBefore(LocalDate.now());
    }

    @GetMapping("/prospects/by-domain")
    public List<Prospect> prospectsByDomain(@RequestParam String domain) {
        return prospects.findByEmailLike("%@" + domain);
    }
}
