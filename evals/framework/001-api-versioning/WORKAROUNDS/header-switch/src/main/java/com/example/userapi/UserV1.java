package com.example.userapi;

/** The original response shape. Existing clients depend on this exact contract. */
public record UserV1(Long id, String name, String email) {
}
