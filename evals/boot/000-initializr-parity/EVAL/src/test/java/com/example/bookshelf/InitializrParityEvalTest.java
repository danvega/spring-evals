package com.example.bookshelf;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hidden eval assertions: the generated project must boot on a current
 * Boot 4 stack with JPA and H2 wired, serve the starter endpoint, and be
 * hand-written rather than generator output.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InitializrParityEvalTest {

    @Autowired
    Environment environment;

    @Autowired
    ApplicationContext context;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void applicationBootsAndServesTheStarterEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                "http://localhost:" + environment.getProperty("local.server.port") + "/api/books")).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().strip())
                .as("GET /api/books must return an empty JSON array for now")
                .isEqualTo("[]");
    }

    @Test
    void persistenceIsWiredAndReady() {
        assertThat(context.getBeanProvider(DataSource.class).getIfAvailable())
                .as("a DataSource must be auto-configured; JPA plus H2 must be on the classpath and wired")
                .isNotNull();
        assertThat(context.containsBean("entityManagerFactory"))
                .as("JPA must be configured, not just the H2 driver")
                .isTrue();
    }

    @Test
    void runsOnACurrentSpringBoot4Generation() {
        String bootVersion = org.springframework.boot.SpringBootVersion.getVersion();
        assertThat(bootVersion)
                .as("the project must run on a current Boot 4 generation, got %s", bootVersion)
                .startsWith("4.");
    }

}
