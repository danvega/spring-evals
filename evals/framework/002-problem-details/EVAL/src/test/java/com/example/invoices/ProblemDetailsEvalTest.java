package com.example.invoices;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hidden eval assertions: missing invoices must produce an RFC 9457
 * problem details response carrying the contract's members, and the
 * happy path must be untouched.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProblemDetailsEvalTest {

    @Autowired
    Environment environment;

    private final HttpClient http = HttpClient.newHttpClient();

    private final JsonMapper json = JsonMapper.builder().build();

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

        JsonNode problem = json.readTree(response.body());
        assertThat(problem.path("title").toString())
                .as("the title member is part of the partner contract")
                .isEqualTo("\"Invoice Not Found\"");
        assertThat(problem.path("status").toString())
                .as("the status member must repeat the HTTP status")
                .isEqualTo("404");
        assertThat(problem.path("detail").toString())
                .as("the detail member must name the requested invoice id")
                .contains("999");
        assertThat(problem.path("invoice_id").toString())
                .as("the requested id must be carried in the invoice_id property")
                .contains("999");
    }
}
