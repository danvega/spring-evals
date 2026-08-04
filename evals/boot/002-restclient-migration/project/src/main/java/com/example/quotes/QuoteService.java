package com.example.quotes;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class QuoteService {

    private final RestTemplate restTemplate;
    private final Environment environment;

    public QuoteService(RestTemplateBuilder restTemplateBuilder, Environment environment) {
        this.restTemplate = restTemplateBuilder.build();
        this.environment = environment;
    }

    public List<Quote> fetchQuotes() {
        Quote[] quotes = restTemplate.getForObject(partnerBaseUrl() + "/partner/quotes", Quote[].class);
        return quotes == null ? List.of() : Arrays.asList(quotes);
    }

    /**
     * The partner base URL comes from configuration in real deployments.
     * Locally it falls back to this app's own port, where the partner
     * stub is served. Keep this resolution logic as is.
     */
    private String partnerBaseUrl() {
        String configured = environment.getProperty("partner.api.base-url");
        if (configured != null) {
            return configured;
        }
        return "http://localhost:" + environment.getProperty("local.server.port", "8080");
    }
}
