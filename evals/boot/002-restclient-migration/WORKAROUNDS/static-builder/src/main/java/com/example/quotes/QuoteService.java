package com.example.quotes;

import java.util.Arrays;
import java.util.List;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class QuoteService {

    private final RestClient restClient;
    private final Environment environment;

    public QuoteService(Environment environment) {
        RestClient.Builder builder = RestClient.builder();
        this.restClient = builder.build();
        this.environment = environment;
    }

    public List<Quote> fetchQuotes() {
        Quote[] quotes = restClient.get()
                .uri(partnerBaseUrl() + "/partner/quotes")
                .retrieve()
                .body(Quote[].class);
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
