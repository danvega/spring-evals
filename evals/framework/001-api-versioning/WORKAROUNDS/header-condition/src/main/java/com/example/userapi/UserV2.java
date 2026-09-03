package com.example.userapi;

/** The v2 response shape: name split for the mobile clients. */
public record UserV2(Long id, String firstName, String lastName, String email) {
}
