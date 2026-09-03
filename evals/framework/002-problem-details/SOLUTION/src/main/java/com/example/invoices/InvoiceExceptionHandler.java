package com.example.invoices;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class InvoiceExceptionHandler {

    @ExceptionHandler(InvoiceNotFoundException.class)
    public ProblemDetail handleInvoiceNotFound(InvoiceNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Invoice Not Found");
        problem.setProperty("invoice_id", exception.getInvoiceId());
        return problem;
    }
}
