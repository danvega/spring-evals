package com.example.catalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BasicAuthFilter extends OncePerRequestFilter {

    private record Account(String password, Set<String> roles) {
    }

    private static final Map<String, Account> ACCOUNTS = Map.of(
            "user", new Account("user-pass", Set.of("USER")),
            "admin", new Account("admin-pass", Set.of("USER", "ADMIN")));

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean publicRead = "GET".equals(request.getMethod())
                && (path.equals("/api/products") || path.matches("/api/products/[^/]+"));
        if (publicRead) {
            chain.doFilter(request, response);
            return;
        }

        Account account = authenticate(request.getHeader("Authorization"));
        if (account == null) {
            response.setHeader("WWW-Authenticate", "Basic realm=\"catalog\"");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        if (path.startsWith("/api/admin/") && !account.roles().contains("ADMIN")) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }

    private static Account authenticate(String header) {
        if (header == null || !header.startsWith("Basic ")) {
            return null;
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        int colon = decoded.indexOf(':');
        if (colon < 0) {
            return null;
        }
        Account account = ACCOUNTS.get(decoded.substring(0, colon));
        return account != null && account.password().equals(decoded.substring(colon + 1)) ? account : null;
    }
}
