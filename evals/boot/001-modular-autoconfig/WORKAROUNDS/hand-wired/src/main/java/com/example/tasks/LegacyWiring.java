package com.example.tasks;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.h2.server.web.JakartaWebServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hand-rolled replacements for the two auto-configurations the Boot 4
 * upgrade dropped. Behaviorally complete, but it never adds the Boot 4
 * modules, so the required pom patterns must reject it.
 */
@Configuration
class LegacyWiring {

    @Bean(initMethod = "migrate")
    Flyway flyway(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).load();
    }

    @Bean
    ServletRegistrationBean<JakartaWebServlet> h2Console() {
        var registration = new ServletRegistrationBean<>(new JakartaWebServlet(), "/h2-console/*");
        registration.addInitParameter("-webAllowOthers", "");
        return registration;
    }
}
