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

    private static final int AUTHORS = 30;
    private static final int BOOKS_PER_AUTHOR = 10;

    @Autowired
    Environment environment;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private final HttpClient http = HttpClient.newHttpClient();

    private String get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + environment.getProperty("local.server.port") + path))
                .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private static int occurrences(String haystack, String needle) {
        return haystack.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    @Test
    void fullResponseWithAHandfulOfQueries() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        String body = get("/api/authors");

        for (int author = 1; author <= AUTHORS; author++) {
            assertThat(body)
                    .as("every author must still be in the response")
                    .contains("\"Author " + author + "\"");
            for (int book = 1; book <= BOOKS_PER_AUTHOR; book++) {
                assertThat(body)
                        .as("every book title must still be in the response")
                        .contains("Book " + book + " by Author " + author);
            }
        }
        assertThat(occurrences(body, "\"Author 13\""))
                .as("each author must appear once; the response shape must not change")
                .isEqualTo(1);

        long statements = statistics.getPrepareStatementCount();
        assertThat(statements)
                .as("one request issued %d SQL statements; the N+1 pattern is still there", statements)
                .isLessThanOrEqualTo(5);
        assertThat(statements)
                .as("the request was served without querying the database at all; "
                        + "the query pattern has to be fixed, not hidden behind a cache or a canned response")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void repeatRequestsStayConstant() throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        get("/api/authors");
        statistics.clear();
        String body = get("/api/authors");
        long statements = statistics.getPrepareStatementCount();

        assertThat(body)
                .as("a repeated request must return the same full data")
                .contains("\"Author " + AUTHORS + "\"")
                .contains("Book " + BOOKS_PER_AUTHOR + " by Author " + AUTHORS);
        assertThat(statements)
                .as("a warm request issued %d SQL statements; the query count per request must be constant "
                        + "and independent of the number of authors", statements)
                .isBetween(1L, 5L);
    }
}
