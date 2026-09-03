package com.example.invoices;

public class InvoiceNotFoundException extends RuntimeException {

    private final Long invoiceId;

    public InvoiceNotFoundException(Long invoiceId) {
        super("Invoice " + invoiceId + " does not exist");
        this.invoiceId = invoiceId;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }
}
