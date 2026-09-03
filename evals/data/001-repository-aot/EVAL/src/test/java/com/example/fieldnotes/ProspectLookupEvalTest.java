package com.example.fieldnotes;

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
 * Hidden eval assertions: the prospect lookup that blew up in production
 * must work, with exact ends-with semantics.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProspectLookupEvalTest {

    @Autowired
    Environment environment;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + environment.getProperty("local.server.port") + path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void prospectsByDomainReturnsExactlyTheMatchingProspects() throws Exception {
        HttpResponse<String> response = get("/api/prospects/by-domain?domain=initech.example");

        assertThat(response.statusCode())
                .as("GET /api/prospects/by-domain must work; this is the lookup that failed in production")
                .isEqualTo(200);
        assertThat(response.body())
                .as("prospects whose email ends with @initech.example must be returned")
                .contains("peter@initech.example")
                .contains("sales@initech.example");
        assertThat(response.body())
                .as("only emails that END with the domain may match; substring matches are wrong")
                .doesNotContain("gavin@hooli.example")
                .doesNotContain("anna@initech.example.eu");
    }

    @Test
    void otherLookupsStillWork() throws Exception {
        HttpResponse<String> customers = get("/api/customers/active");
        assertThat(customers.statusCode()).isEqualTo(200);
        assertThat(customers.body()).contains("Lovelace").doesNotContain("Dijkstra");

        HttpResponse<String> invoices = get("/api/invoices/overdue");
        assertThat(invoices.statusCode()).isEqualTo(200);
        assertThat(invoices.body()).contains("120");
    }
}
