package com.example.quotes;

import java.util.List;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.util.DefaultUriBuilderFactory;

@Service
public class QuoteService {

    private final PartnerQuotesClient partnerQuotesClient;
    private final Environment environment;

    public QuoteService(PartnerQuotesClient partnerQuotesClient, Environment environment) {
        this.partnerQuotesClient = partnerQuotesClient;
        this.environment = environment;
    }

    public List<Quote> fetchQuotes() {
        List<Quote> quotes = partnerQuotesClient.quotes(new DefaultUriBuilderFactory(partnerBaseUrl()));
        return quotes == null ? List.of() : quotes;
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
