package com.example.transfers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hidden eval assertions: a failed transfer must leave every balance
 * untouched, and successful transfers must still move money. Ordered so
 * the failure check runs against pristine seed balances.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TransactionalityEvalTest {

    @Autowired
    Environment environment;

    private final HttpClient http = HttpClient.newHttpClient();

    private String baseUrl() {
        return "http://localhost:" + environment.getProperty("local.server.port");
    }

    private HttpResponse<String> transfer(String from, String to, String amount) throws Exception {
        String body = "{\"from\": \"%s\", \"to\": \"%s\", \"amount\": %s}".formatted(from, to, amount);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/transfers"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String balanceOf(String accountId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/accounts/" + accountId))
                .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    @Test
    @Order(1)
    void failedTransferLeavesAllBalancesUntouched() throws Exception {
        HttpResponse<String> response = transfer("alice", "carol", "100");

        assertThat(response.statusCode())
                .as("a transfer to a frozen account must fail")
                .isGreaterThanOrEqualTo(400);
        assertThat(balanceOf("alice"))
                .as("the withdrawal must be rolled back; alice's money disappeared")
                .contains("1000.0");
        assertThat(balanceOf("carol"))
                .as("the frozen account must be untouched")
                .contains("250.0");
    }

    @Test
    @Order(2)
    void successfulTransferMovesMoneyBothWays() throws Exception {
        HttpResponse<String> response = transfer("alice", "bob", "150");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(balanceOf("alice")).contains("850.0");
        assertThat(balanceOf("bob")).contains("650.0");
    }
}
