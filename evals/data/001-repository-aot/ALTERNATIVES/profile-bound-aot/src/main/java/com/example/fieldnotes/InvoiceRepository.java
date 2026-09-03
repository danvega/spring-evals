package com.example.fieldnotes;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.repository.ListCrudRepository;

public interface InvoiceRepository extends ListCrudRepository<Invoice, Long> {

    List<Invoice> findByPaidFalseAndDueDateBefore(LocalDate date);
}
