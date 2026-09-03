package com.example.orders;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.stereotype.Component;

/**
 * JSON contract for the orders API: snake_case field names
 * (customer_name, order_date, total_amount). Jackson 3 already writes
 * dates as ISO-8601 strings, so only the naming strategy is configured.
 */
@Component
public class JacksonConfig implements JsonMapperBuilderCustomizer {

    @Override
    public void customize(JsonMapper.Builder builder) {
        builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }
}
