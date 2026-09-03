package com.example.invoices;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class InvoiceNotFoundException extends ErrorResponseException {

    private final Long invoiceId;

    public InvoiceNotFoundException(Long invoiceId) {
        super(HttpStatus.NOT_FOUND);
        this.invoiceId = invoiceId;
        setTitle("Invoice Not Found");
        setDetail("Invoice " + invoiceId + " does not exist");
        getBody().setProperty("invoice_id", invoiceId);
    }

    public Long getInvoiceId() {
        return invoiceId;
    }
}
