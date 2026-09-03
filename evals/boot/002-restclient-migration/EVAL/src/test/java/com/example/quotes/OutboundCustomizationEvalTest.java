package com.example.quotes;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.filter.OncePerRequestFilter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hidden eval assertion: platform-level client customizations must reach the
 * outbound partner call. A test-owned RestClientCustomizer stamps a header on
 * every auto-configured client, and a test-owned servlet filter records what
 * the partner stub actually received. A client built outside the
 * auto-configured machinery never carries the header.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OutboundCustomizationEvalTest {

    static final String PROBE_HEADER = "X-Platform-Probe";
    static final String PROBE_VALUE = "applied-by-platform-customizer";

    @TestConfiguration(proxyBeanMethods = false)
    static class PlatformProbeConfig {

        @Bean
        RestClientCustomizer platformProbeCustomizer() {
            return builder -> builder.defaultHeader(PROBE_HEADER, PROBE_VALUE);
        }

        @Bean
        PartnerRequestRecorder partnerRequestRecorder() {
            return new PartnerRequestRecorder();
        }
    }

    /** Records the probe header of every request the partner stub receives. */
    static class PartnerRequestRecorder extends OncePerRequestFilter {

        final List<String> probeHeaders = new CopyOnWriteArrayList<>();

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {
            if (request.getRequestURI().startsWith("/partner/")) {
                probeHeaders.add(String.valueOf(request.getHeader(PROBE_HEADER)));
            }
            chain.doFilter(request, response);
        }
    }

    @Autowired
    Environment environment;

    @Autowired
    PartnerRequestRecorder recorder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void platformCustomizerReachesThePartnerCall() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + environment.getProperty("local.server.port") + "/api/quotes"))
                .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(recorder.probeHeaders)
                .as("the partner stub must receive the header added by the platform RestClientCustomizer; "
                        + "a hand-built client bypasses the auto-configured builder and its customizations")
                .isNotEmpty()
                .containsOnly(PROBE_VALUE);
    }
}
