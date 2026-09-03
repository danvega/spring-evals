package com.example.transfers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.Callable;

import javax.sql.DataSource;

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
 * untouched, and successful transfers must still move money. Two of the
 * failures are injected at the database, which refuses every credit, or
 * every debit, while one transfer runs. A fix that commits one leg on its
 * own, or repairs it afterwards, is visible from outside. Ordered so every
 * failure check runs against pristine seed balances.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TransactionalityEvalTest {

    @Autowired
    Environment environment;

    @Autowired
    DataSource dataSource;

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

    /** Installs a row trigger on the account table around one call and always removes it again. */
    private HttpResponse<String> withVeto(String triggerName, Callable<HttpResponse<String>> call)
            throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER " + triggerName + " BEFORE UPDATE ON account FOR EACH ROW CALL \""
                    + BalanceMoveVeto.class.getName() + "\"");
        }
        try {
            return call.call();
        } finally {
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.execute("DROP TRIGGER IF EXISTS " + triggerName);
            }
        }
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
    void insufficientFundsLeavesBothBalancesUntouched() throws Exception {
        HttpResponse<String> response = transfer("bob", "alice", "5000");

        assertThat(response.statusCode())
                .as("a transfer larger than the source balance must fail")
                .isGreaterThanOrEqualTo(400);
        assertThat(balanceOf("bob"))
                .as("the source account must be untouched")
                .contains("500.0");
        assertThat(balanceOf("alice"))
                .as("nothing may be credited when the withdrawal fails; alice gained money")
                .contains("1000.0");
    }

    @Test
    @Order(3)
    void depositRefusedByDatabaseRollsBackWithdrawal() throws Exception {
        HttpResponse<String> response = withVeto("REFUSE_CREDITS", () -> transfer("alice", "bob", "100"));

        assertThat(response.statusCode())
                .as("a transfer whose deposit the database refuses must fail")
                .isGreaterThanOrEqualTo(400);
        assertThat(balanceOf("alice"))
                .as("the withdrawal must be rolled back when the deposit fails inside the database; "
                        + "alice's money disappeared")
                .contains("1000.0");
        assertThat(balanceOf("bob"))
                .as("the refused deposit must not be visible")
                .contains("500.0");
    }

    @Test
    @Order(4)
    void withdrawalRefusedByDatabaseRollsBackDeposit() throws Exception {
        HttpResponse<String> response = withVeto("REFUSE_DEBITS", () -> transfer("alice", "bob", "100"));

        assertThat(response.statusCode())
                .as("a transfer whose withdrawal the database refuses must fail")
                .isGreaterThanOrEqualTo(400);
        assertThat(balanceOf("bob"))
                .as("the deposit must be rolled back when the withdrawal fails inside the database; "
                        + "bob gained money")
                .contains("500.0");
        assertThat(balanceOf("alice"))
                .as("the refused withdrawal must not be visible")
                .contains("1000.0");
    }

    @Test
    @Order(5)
    void successfulTransferMovesMoneyBothWays() throws Exception {
        HttpResponse<String> response = transfer("alice", "bob", "150");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(balanceOf("alice")).contains("850.0");
        assertThat(balanceOf("bob")).contains("650.0");
    }
}
