package com.example.orders;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hidden eval assertions. These tests are injected after the agent finishes
 * and are intentionally black-box: they only use the HTTP API and the
 * application context, never the project's own classes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JsonContractEvalTest {

    private static final Pattern ISO_DATE_FIELD =
            Pattern.compile("\"order_date\"\\s*:\\s*\"\\d{4}-\\d{2}-\\d{2}\"");
    private static final Pattern ID_FIELD =
            Pattern.compile("\"id\"\\s*:\\s*(\\d+)");

    @Autowired
    Environment environment;

    @Autowired
    ApplicationContext context;

    private final HttpClient http = HttpClient.newHttpClient();

    private String baseUrl() {
        return "http://localhost:" + environment.getProperty("local.server.port");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void listUsesSnakeCaseFieldNames() throws Exception {
        HttpResponse<String> response = get("/api/orders");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .as("list response must use the published snake_case contract")
                .contains("\"customer_name\"")
                .contains("\"order_date\"")
                .contains("\"total_amount\"")
                .doesNotContain("\"customerName\"")
                .doesNotContain("\"orderDate\"")
                .doesNotContain("\"totalAmount\"");
    }

    @Test
    void listSerializesDatesAsIsoStrings() throws Exception {
        HttpResponse<String> response = get("/api/orders");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(ISO_DATE_FIELD.matcher(response.body()).find())
                .as("order_date must be an ISO string like \"2026-07-15\", got: %s", response.body())
                .isTrue();
        assertThat(response.body())
                .as("order_date must never be a numeric timestamp or array")
                .doesNotContainPattern("\"order_date\"\\s*:\\s*\\[")
                .doesNotContainPattern("\"order_date\"\\s*:\\s*\\d");
    }

    @Test
    void createAcceptsSnakeCaseRequestBody() throws Exception {
        String body = """
                {
                  "customer_name": "Ada Lovelace",
                  "order_date": "2026-08-01",
                  "status": "PENDING",
                  "total_amount": 19.99
                }
                """;

        HttpResponse<String> response = postJson("/api/orders", body);

        assertThat(response.statusCode())
                .as("POST /api/orders must accept the snake_case contract")
                .isIn(200, 201);
        assertThat(response.body())
                .contains("Ada Lovelace")
                .contains("\"customer_name\"");
    }

    @Test
    void exportMatchesTheApiContract() throws Exception {
        String body = """
                {
                  "customer_name": "Katherine Johnson",
                  "order_date": "2026-08-02",
                  "status": "DELIVERED",
                  "total_amount": 123.45
                }
                """;
        HttpResponse<String> created = postJson("/api/orders", body);
        assertThat(created.statusCode()).isIn(200, 201);

        Matcher idMatcher = ID_FIELD.matcher(created.body());
        assertThat(idMatcher.find())
                .as("create response must contain a numeric id, got: %s", created.body())
                .isTrue();
        String id = idMatcher.group(1);

        HttpResponse<String> export = get("/api/orders/" + id + "/export");

        assertThat(export.statusCode()).isEqualTo(200);
        assertThat(export.body())
                .as("export must use the same mapper configuration as the API, "
                        + "not a hand-built one with default settings")
                .contains("\"customer_name\"")
                .contains("\"2026-08-02\"")
                .doesNotContain("\"customerName\"");
    }

    @Test
    void springManagedJackson3MapperIsStillInPlay() {
        assertThat(context.getBeanProvider(tools.jackson.databind.json.JsonMapper.class).getIfAvailable())
                .as("the auto-configured Jackson 3 JsonMapper must still exist; "
                        + "reintroducing Jackson 2 or replacing the JSON stack is not a valid fix")
                .isNotNull();
    }
}
