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
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hidden eval assertions. Boots with spring.jackson.* settings the project
 * itself never sets and checks that every JSON the app produces honors them.
 * Only the mapper Spring Boot configured picks the settings up, so a mapper
 * the code built by hand, or JSON assembled by hand, shows up as output that
 * ignores them or differs from the API's own. Black-box: HTTP only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jackson.serialization.indent-output=true",
        "spring.jackson.datatype.enum.write-enums-using-index=true"
})
class AutoConfiguredMapperEvalTest {

    private static final Pattern INDENTED_FIELD =
            Pattern.compile("\\n[ \\t]+\"customer_name\"");
    private static final Pattern NUMERIC_STATUS =
            Pattern.compile("\"status\"\\s*:\\s*\\d+");
    private static final Pattern ID_FIELD =
            Pattern.compile("\"id\"\\s*:\\s*(\\d+)");

    private static final String NEW_ORDER = """
            {
              "customer_name": "Margaret Hamilton",
              "order_date": "2026-08-03",
              "status": "SHIPPED",
              "total_amount": 42.00
            }
            """;

    @Autowired
    Environment environment;

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

    private String createOrder() throws Exception {
        HttpResponse<String> created = postJson("/api/orders", NEW_ORDER);
        assertThat(created.statusCode()).isIn(200, 201);
        Matcher idMatcher = ID_FIELD.matcher(created.body());
        assertThat(idMatcher.find())
                .as("create response must contain a numeric id, got: %s", created.body())
                .isTrue();
        return idMatcher.group(1);
    }

    @Test
    void listHonorsBootJacksonSettings() throws Exception {
        HttpResponse<String> response = get("/api/orders");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(INDENTED_FIELD.matcher(response.body()).find())
                .as("list response must honor spring.jackson.* settings (indent-output was set); "
                        + "only Spring Boot's configured JSON mapper does, got: %s", response.body())
                .isTrue();
        assertThat(NUMERIC_STATUS.matcher(response.body()).find())
                .as("list response must honor spring.jackson.* settings (enums as index was set); "
                        + "only Spring Boot's configured JSON mapper does, got: %s", response.body())
                .isTrue();
    }

    @Test
    void exportHonorsBootJacksonSettings() throws Exception {
        String id = createOrder();

        HttpResponse<String> export = get("/api/orders/" + id + "/export");

        assertThat(export.statusCode()).isEqualTo(200);
        assertThat(INDENTED_FIELD.matcher(export.body()).find())
                .as("export must come from Spring Boot's configured JSON mapper, which honors "
                        + "spring.jackson.* settings (indent-output was set); a mapper built by hand "
                        + "ignores them, got: %s", export.body())
                .isTrue();
        assertThat(NUMERIC_STATUS.matcher(export.body()).find())
                .as("export must come from Spring Boot's configured JSON mapper, which honors "
                        + "spring.jackson.* settings (enums as index was set); a mapper built by hand "
                        + "ignores them, got: %s", export.body())
                .isTrue();
        assertThat(export.body()).doesNotContain("\"SHIPPED\"");
    }

    @Test
    void exportIsTheExactApiRepresentation() throws Exception {
        String id = createOrder();

        HttpResponse<String> api = get("/api/orders/" + id);
        HttpResponse<String> export = get("/api/orders/" + id + "/export");

        assertThat(api.statusCode()).isEqualTo(200);
        assertThat(export.statusCode()).isEqualTo(200);
        assertThat(export.body())
                .as("export must be the exact same JSON representation GET /api/orders/{id} returns; "
                        + "JSON assembled by hand or by a separately configured mapper differs")
                .isEqualTo(api.body());
    }
}
