package com.example.payments;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hidden eval assertions, verified over HTTP: transient gateway failures
 * must be retried away, and report generation must be capped at 2
 * concurrent executions with excess callers queuing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResilienceEvalTest {

    @Autowired
    Environment environment;

    // Pinned fixtures, so the judge rejects any candidate that edits them.
    // Their counters are ground truth; the controller that echoes them is not.
    @Autowired
    PaymentGateway paymentGateway;

    @Autowired
    ReportGenerator reportGenerator;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> post(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + environment.getProperty("local.server.port") + path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + environment.getProperty("local.server.port") + path))
                .GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void transientGatewayFailuresAreRetriedAway() throws Exception {
        HttpResponse<String> response = post("/api/payments/EVAL-7001/charge");

        assertThat(response.statusCode())
                .as("a single API call must succeed even though the gateway drops the first two attempts")
                .isEqualTo(200);
        assertThat(response.body()).contains("PAY-EVAL-7001");
        assertThat(paymentGateway.attemptsFor("EVAL-7001"))
                .as("the gateway itself must record exactly 3 calls (2 failures + 1 success)")
                .isEqualTo(3);
    }

    @Test
    void reportGenerationIsCappedAtTwoConcurrentExecutions() throws Exception {
        int requests = 6;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> statuses = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            statuses.add(pool.submit(() -> {
                start.await();
                return post("/api/reports/run").statusCode();
            }));
        }
        long startedAt = System.nanoTime();
        start.countDown();
        for (Future<Integer> status : statuses) {
            assertThat(status.get())
                    .as("queued report requests must eventually succeed, not fail")
                    .isEqualTo(200);
        }
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
        pool.shutdown();

        // Six requests capped at two run in three waves of a 300ms generation,
        // so a real cap cannot finish sooner. Unthrottled work, or a cached
        // report generated once, comes back in roughly one wave. Only a lower
        // bound is safe here: host load pushes elapsed time up, never down.
        assertThat(elapsedMillis)
                .as("6 requests capped at 2 must take at least 3 generation waves, took %sms", elapsedMillis)
                .isGreaterThan(700);

        HttpResponse<String> stats = get("/api/reports/stats");
        assertThat(stats.statusCode()).isEqualTo(200);
        assertThat(stats.body())
                .as("observed concurrency must never exceed 2, got: %s", stats.body())
                .containsAnyOf("\"maxConcurrent\":1", "\"maxConcurrent\":2");
        assertThat(reportGenerator.maxObservedConcurrency())
                .as("the generator itself must never have seen more than 2 at once")
                .isBetween(1, 2);
    }
}
