package com.example.orders;

import java.math.BigDecimal;
import java.time.LocalDate;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Published JSON contract: snake_case field names
 * (customer_name, order_date, total_amount).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
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
