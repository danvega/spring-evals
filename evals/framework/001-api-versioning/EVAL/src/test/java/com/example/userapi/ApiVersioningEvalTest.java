package com.example.userapi;

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
 * Hidden eval assertions: one path, two versions selected by the
 * X-API-Version header, and headerless requests default to v1.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiVersioningEvalTest {

    @Autowired
    Environment environment;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> getUser(String version) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(
                "http://localhost:" + environment.getProperty("local.server.port") + "/api/users/1")).GET();
        if (version != null) {
            builder.header("X-API-Version", version);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void version1KeepsTheOriginalShape() throws Exception {
        HttpResponse<String> response = getUser("1.0");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"name\"")
                .contains("Grace Hopper")
                .contains("grace@example.com")
                .doesNotContain("\"firstName\"");
    }

    @Test
    void version2SplitsTheName() throws Exception {
        HttpResponse<String> response = getUser("2.0");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"firstName\"")
                .contains("Grace")
                .contains("\"lastName\"")
                .contains("Hopper")
                .contains("grace@example.com")
                .doesNotContain("\"name\"");
    }

    @Test
    void missingVersionHeaderDefaultsToV1() throws Exception {
        HttpResponse<String> response = getUser(null);

        assertThat(response.statusCode())
                .as("requests without a version header must not break")
                .isEqualTo(200);
        assertThat(response.body())
                .contains("\"name\"")
                .doesNotContain("\"firstName\"");
    }
}
