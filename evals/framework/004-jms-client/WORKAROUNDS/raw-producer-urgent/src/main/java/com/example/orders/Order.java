package com.example.orders;

public record Order(String id, String item, int quantity, boolean urgent) {
}
