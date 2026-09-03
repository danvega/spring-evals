package com.example.orders;

import jakarta.jms.ConnectionFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.core.JmsClient;

@Configuration(proxyBeanMethods = false)
class MessagingConfig {

    @Bean
    JmsClient jmsClient(ConnectionFactory connectionFactory) {
        return JmsClient.builder(connectionFactory).build();
    }
}
