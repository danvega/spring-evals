package com.example.quotes;

import org.springframework.boot.restclient.autoconfigure.RestClientBuilderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class PartnerClientConfig {

    @Bean
    RestClient partnerRestClient(RestClientBuilderConfigurer configurer) {
        return configurer.configure(RestClient.builder()).build();
    }
}
