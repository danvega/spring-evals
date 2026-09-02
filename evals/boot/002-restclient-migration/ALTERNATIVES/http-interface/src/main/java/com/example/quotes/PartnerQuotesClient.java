package com.example.quotes;

import java.util.List;

import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.util.UriBuilderFactory;

@HttpExchange
public interface PartnerQuotesClient {

    /** The base URL is resolved per call by the caller, not by group properties. */
    @GetExchange("/partner/quotes")
    List<Quote> quotes(UriBuilderFactory partnerBaseUrl);
}
