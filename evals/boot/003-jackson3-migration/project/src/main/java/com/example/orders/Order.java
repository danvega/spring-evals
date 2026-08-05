package com.example.orders;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Order(
        Long id,
        String customerName,
        LocalDate orderDate,
        OrderStatus status,
        BigDecimal totalAmount
) {

    public Order withId(Long newId) {
        return new Order(newId, customerName, orderDate, status, totalAmount);
    }
}
