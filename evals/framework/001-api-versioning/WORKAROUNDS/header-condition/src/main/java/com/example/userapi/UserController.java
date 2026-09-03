package com.example.userapi;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/{id}")
    public ResponseEntity<UserV1> findByIdV1(@PathVariable Long id) {
        if (id != 1L && id != 2L) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(id == 1L
                ? new UserV1(1L, "Grace Hopper", "grace@example.com")
                : new UserV1(2L, "Alan Turing", "alan@example.com"));
    }

    @GetMapping(value = "/{id}", headers = "x-api-version=2.0")
    public ResponseEntity<UserV2> findByIdV2(@PathVariable Long id) {
        if (id != 1L && id != 2L) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(id == 1L
                ? new UserV2(1L, "Grace", "Hopper", "grace@example.com")
                : new UserV2(2L, "Alan", "Turing", "alan@example.com"));
    }
}
