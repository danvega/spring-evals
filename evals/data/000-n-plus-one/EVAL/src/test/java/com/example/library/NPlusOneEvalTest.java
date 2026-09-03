package com.example.library;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hidden eval assertions: the full author list must be served with a
 * constant, single-digit number of SQL statements per request. Hibernate
 * statistics are forced on here so they cannot be disabled by the fix.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class NPlusOneEvalTest {

    @Autowired
    Environment environment;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void fullResponseWithAHandfulOfQueries() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + environment.getProperty("local.server.port") + "/api/authors"))
                .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        for (int author = 1; author <= 30; author++) {
            assertThat(response.body())
                    .as("every author must still be in the response")
                    .contains("\"Author " + author + "\"");
        }
        assertThat(response.body())
                .as("book titles must still be in the response")
                .contains("Book 7 by Author 13")
                .contains("Book 10 by Author 30");

        long statements = statistics.getPrepareStatementCount();
        assertThat(statements)
                .as("one request issued %d SQL statements; the N+1 pattern is still there", statements)
                .isLessThanOrEqualTo(5);
    }
}
