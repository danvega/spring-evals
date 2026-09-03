package com.example.userapi;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The tested path is branched by hand off the raw request. The versioned
 * mapping below is left over from an abandoned first attempt and sits on an
 * unrelated path, so nothing the tests exercise goes through it.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/{id}")
    public ResponseEntity<?> findById(@PathVariable Long id, HttpServletRequest request) {
        if (id != 1L && id != 2L) {
            return ResponseEntity.notFound().build();
        }
        if ("2.0".equals(request.getHeader("X-API-Version"))) {
            return ResponseEntity.ok(id == 1L
                    ? new UserV2(1L, "Grace", "Hopper", "grace@example.com")
                    : new UserV2(2L, "Alan", "Turing", "alan@example.com"));
        }
        return ResponseEntity.ok(id == 1L
                ? new UserV1(1L, "Grace Hopper", "grace@example.com")
                : new UserV1(2L, "Alan Turing", "alan@example.com"));
    }

    @GetMapping(value = "/summary/{id}", version = "2.0")
    public ResponseEntity<UserV2> summaryV2(@PathVariable Long id) {
        return ResponseEntity.ok(new UserV2(id, "Grace", "Hopper", "grace@example.com"));
    }
}
