package com.example.quotes;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Configuration(proxyBeanMethods = false)
class PartnerClientConfig {

    @Bean
    RestClient partnerRestClient(RestClient.Builder builder) {
        return builder
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw new RestClientException("partner quotes API returned " + response.getStatusCode());
                })
                .build();
    }
}
