package com.example.tasks;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hidden eval assertions: the Flyway migration must actually run and the H2
 * console must be reachable, on Spring Boot 4's modular auto-configuration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ModularAutoConfigEvalTest {

    @Autowired
    Environment environment;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + environment.getProperty("local.server.port") + path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void tasksEndpointServesSeededData() throws Exception {
        HttpResponse<String> response = get("/api/tasks");

        assertThat(response.statusCode())
                .as("GET /api/tasks must work again, which requires the migration to have run")
                .isEqualTo(200);
        assertThat(response.body())
                .contains("Write release notes")
                .contains("Ship taskhub 2.0");
    }

    @Test
    void flywayActuallyRanTheMigration() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from \"flyway_schema_history\" where \"success\" = true", Integer.class);

        assertThat(applied)
                .as("the Flyway schema history must record a real migration run; "
                        + "creating the table by hand is not a fix")
                .isNotNull()
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void h2ConsoleIsBackAtItsUsualPath() throws Exception {
        HttpResponse<String> response = get("/h2-console");

        assertThat(response.statusCode())
                .as("the H2 console must respond at /h2-console (200 or a redirect into the console)")
                .isIn(200, 301, 302, 307, 308);
    }
}
