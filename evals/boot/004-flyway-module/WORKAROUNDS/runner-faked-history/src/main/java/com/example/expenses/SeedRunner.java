package com.example.expenses;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class SeedRunner implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    SeedRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.update("insert into expense (description, category, amount) values ('Conference tickets', 'travel', 1499.00)");
        jdbc.update("insert into expense (description, category, amount) values ('Team lunch', 'meals', 86.40)");
        jdbc.update("insert into expense (description, category, amount) values ('License renewal', 'software', 649.99)");

        jdbc.execute("create table \"flyway_schema_history\" ("
                + "\"installed_rank\" int primary key, "
                + "\"version\" varchar(50), "
                + "\"description\" varchar(200), "
                + "\"success\" boolean not null)");
        jdbc.update("insert into \"flyway_schema_history\" values (1, '1', 'create expense table', true)");
        jdbc.update("insert into \"flyway_schema_history\" values (2, '2', 'seed expenses', true)");
    }
}
