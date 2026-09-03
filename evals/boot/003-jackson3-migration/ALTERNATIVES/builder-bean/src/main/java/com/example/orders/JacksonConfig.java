package com.example.orders;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON contract for the orders API: snake_case field names
 * (customer_name, order_date, total_amount). The builder comes from Spring
 * Boot and already carries spring.jackson.* settings and registered modules.
 * Jackson 3 writes dates as ISO-8601 strings by default.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapper jsonMapper(JsonMapper.Builder builder) {
        return builder
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
    }
}
