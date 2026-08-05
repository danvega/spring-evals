package com.example.expenses;

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
 * Hidden eval assertions: the Flyway migrations must actually run on Spring
 * Boot 4, and the schema and seed data must come from those migrations, not
 * from a schema.sql/data.sql or Hibernate ddl-auto workaround.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FlywayModuleEvalTest {

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
    void expensesEndpointServesSeededData() throws Exception {
        HttpResponse<String> response = get("/api/expenses");

        assertThat(response.statusCode())
                .as("GET /api/expenses must return the seeded expenses, which requires the migrations to have run")
                .isEqualTo(200);
        assertThat(response.body())
                .contains("Conference tickets")
                .contains("Team lunch")
                .contains("License renewal");
    }

    @Test
    void totalEndpointSumsTheSeededExpenses() throws Exception {
        HttpResponse<String> response = get("/api/expenses/total");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .as("the total must add up the three seeded expenses")
                .contains("2235.39");
    }

    @Test
    void flywayActuallyRanBothMigrations() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from \"flyway_schema_history\" where \"success\" = true", Integer.class);

        assertThat(applied)
                .as("the Flyway schema history must record both migrations; "
                        + "creating the schema or the rows by hand is not a fix")
                .isNotNull()
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void schemaDoesNotComeFromAWorkaround() {
        String ddlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto");
        assertThat(ddlAuto == null || ddlAuto.equals("none") || ddlAuto.equals("validate"))
                .as("Hibernate schema generation must stay off; ddl-auto was set to '%s'", ddlAuto)
                .isTrue();

        String hbm2ddl = environment.getProperty("spring.jpa.properties.hibernate.hbm2ddl.auto");
        assertThat(hbm2ddl == null || hbm2ddl.equals("none") || hbm2ddl.equals("validate"))
                .as("native Hibernate schema generation must stay off; hbm2ddl.auto was set to '%s'", hbm2ddl)
                .isTrue();

        assertThat(environment.getProperty("spring.jpa.generate-ddl", Boolean.class, false))
                .as("spring.jpa.generate-ddl must not be used to recreate the schema")
                .isFalse();

        String sqlInitMode = environment.getProperty("spring.sql.init.mode");
        assertThat(sqlInitMode == null || sqlInitMode.equalsIgnoreCase("never"))
                .as("SQL init scripts must not replace the migrations; spring.sql.init.mode was '%s'", sqlInitMode)
                .isTrue();

        assertThat(environment.getProperty("spring.sql.init.schema-locations"))
                .as("custom SQL init schema scripts must not replace the migrations")
                .isNull();
        assertThat(environment.getProperty("spring.sql.init.data-locations"))
                .as("custom SQL init data scripts must not replace the migrations")
                .isNull();

        assertThat(getClass().getResource("/schema.sql"))
                .as("schema.sql must not replace the migrations")
                .isNull();
        assertThat(getClass().getResource("/data.sql"))
                .as("data.sql must not replace the migrations")
                .isNull();
    }
}
