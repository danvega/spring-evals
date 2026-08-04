package com.example.invoices;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hidden eval assertions: missing invoices must produce an RFC 9457
 * problem details response, and the happy path must be untouched.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProblemDetailsEvalTest {

    @Autowired
    Environment environment;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                "http://localhost:" + environment.getProperty("local.server.port") + path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void happyPathIsUnchanged() throws Exception {
        HttpResponse<String> response = get("/api/invoices/1");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Acme Corp");
    }

    @Test
    void missingInvoiceReturnsProblemDetails() throws Exception {
        HttpResponse<String> response = get("/api/invoices/999");

        assertThat(response.statusCode())
                .as("a missing invoice must be a 404, not a 500")
                .isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .as("errors must use the problem+json media type")
                .contains("application/problem+json");
        assertThat(response.body())
                .contains("Invoice Not Found")
                .contains("999")
                .contains("\"invoice_id\"");
    }
}
