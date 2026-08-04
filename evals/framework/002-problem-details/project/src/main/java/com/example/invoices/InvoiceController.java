package com.example.invoices;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final Map<Long, Invoice> invoices = Map.of(
            1L, new Invoice(1L, "Acme Corp", new BigDecimal("1250.00")),
            2L, new Invoice(2L, "Globex", new BigDecimal("840.50")));

    @GetMapping
    public List<Invoice> findAll() {
        return invoices.values().stream().sorted((a, b) -> a.id().compareTo(b.id())).toList();
    }

    @GetMapping("/{id}")
    public Invoice findById(@PathVariable Long id) {
        Invoice invoice = invoices.get(id);
        if (invoice == null) {
            throw new InvoiceNotFoundException(id);
        }
        return invoice;
    }
}
