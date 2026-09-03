package com.example.userapi;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/{id}")
    public ResponseEntity<?> findById(@PathVariable Long id, HttpServletRequest request) {
        if (id != 1L && id != 2L) {
            return ResponseEntity.notFound().build();
        }
        String version = request.getHeader("X-API-Version");
        if ("2.0".equals(version)) {
            return ResponseEntity.ok(id == 1L
                    ? new UserV2(1L, "Grace", "Hopper", "grace@example.com")
                    : new UserV2(2L, "Alan", "Turing", "alan@example.com"));
        }
        return ResponseEntity.ok(id == 1L
                ? new UserV1(1L, "Grace Hopper", "grace@example.com")
                : new UserV1(2L, "Alan Turing", "alan@example.com"));
    }
}
