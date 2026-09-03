package com.example.invoices;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class InvoiceExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(InvoiceNotFoundException.class)
    public ResponseEntity<Object> handleInvoiceNotFound(InvoiceNotFoundException exception, WebRequest request) {
        ProblemDetail problem = createProblemDetail(exception, HttpStatus.NOT_FOUND, exception.getMessage(),
                null, null, request);
        problem.setTitle("Invoice Not Found");
        problem.setProperty("invoice_id", exception.getInvoiceId());
        return handleExceptionInternal(exception, problem, new HttpHeaders(), HttpStatus.NOT_FOUND, request);
    }
}
