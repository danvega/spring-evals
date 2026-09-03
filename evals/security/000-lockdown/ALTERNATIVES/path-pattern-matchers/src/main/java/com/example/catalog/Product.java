package com.example.catalog;

import java.math.BigDecimal;

public record Product(Long id, String name, BigDecimal price) {

    public Product withId(Long newId) {
        return new Product(newId, name, price);
    }
}
