package com.example.orders;

import tools.jackson.databind.PropertyNamingStrategies;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON contract for the orders API: snake_case field names
 * (customer_name, order_date, total_amount). Jackson 3 already writes
 * dates as ISO-8601 strings, so only the naming strategy is configured.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer snakeCaseNaming() {
        return builder -> builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }
}
