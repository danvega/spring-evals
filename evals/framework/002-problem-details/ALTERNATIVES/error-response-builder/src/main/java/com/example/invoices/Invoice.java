package com.example.invoices;

import java.math.BigDecimal;

public record Invoice(Long id, String customer, BigDecimal amount) {
}
