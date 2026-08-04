package com.example.quotes;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hidden eval assertions: the app must start on Boot 4, serve the partner
 * quotes through the outbound HTTP call, and carry the auto-configured
 * RestClient.Builder so platform customizations apply.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RestClientMigrationEvalTest {

    @Autowired
    Environment environment;

    @Autowired
    ApplicationContext context;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void quotesEndpointServesPartnerQuotes() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + environment.getProperty("local.server.port") + "/api/quotes"))
                .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("Grace Hopper")
                .contains("The best way to predict the future is to invent it.");
    }

    @Test
    void autoConfiguredRestClientBuilderIsPresent() {
        assertThat(context.getBeanProvider(RestClient.Builder.class).getIfAvailable())
                .as("the auto-configured RestClient.Builder must exist so platform-level "
                        + "customizations apply to outbound calls")
                .isNotNull();
    }
}
