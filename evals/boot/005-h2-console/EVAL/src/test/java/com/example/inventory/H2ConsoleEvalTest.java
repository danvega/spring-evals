package com.example.inventory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hidden eval assertions: the real H2 console must be reachable again at
 * /h2-console, and the app's own data endpoints must keep working.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class H2ConsoleEvalTest {

    @Autowired
    Environment environment;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private String baseUrl() {
        return "http://localhost:" + environment.getProperty("local.server.port");
    }

    private HttpResponse<String> get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void productListStillServesSeededCatalog() throws Exception {
        HttpResponse<String> response = get(baseUrl() + "/api/products");

        assertThat(response.statusCode())
                .as("GET /api/products must keep working; the fix must not break the data layer")
                .isEqualTo(200);
        assertThat(response.body())
                .contains("Ceramic bearing kit")
                .contains("Torque wrench");
    }

    @Test
    void productByIdStillWorks() throws Exception {
        HttpResponse<String> response = get(baseUrl() + "/api/products/1");

        assertThat(response.statusCode())
                .as("GET /api/products/1 must keep working; the fix must not break the data layer")
                .isEqualTo(200);
        assertThat(response.body()).contains("SKU-1001");
    }

    @Test
    void h2ConsoleRespondsAtItsUsualPath() throws Exception {
        HttpResponse<String> response = get(baseUrl() + "/h2-console");

        assertThat(response.statusCode())
                .as("the H2 console must respond at /h2-console (200 or a redirect into the console)")
                .isIn(200, 301, 302, 307, 308);

        String consolePage = response.body();
        URI consoleUri = URI.create(baseUrl() + "/h2-console");
        if (response.statusCode() >= 300) {
            String location = response.headers().firstValue("Location").orElse("");
            assertThat(location)
                    .as("a redirect from /h2-console must lead into the console, not away from it")
                    .contains("h2-console");

            consoleUri = consoleUri.resolve(location);
            HttpResponse<String> followed = get(consoleUri.toString());
            assertThat(followed.statusCode())
                    .as("the console page behind the redirect must actually render")
                    .isEqualTo(200);
            consolePage = followed.body();
        }
        assertThat(consolePage)
                .as("the page served at /h2-console must be the real H2 console, not a stand-in")
                .containsIgnoringCase("h2 console")
                .contains("login.jsp");

        Matcher bounce = Pattern.compile("login\\.jsp\\?jsessionid=[A-Za-z0-9]+").matcher(consolePage);
        assertThat(bounce.find())
                .as("the real console sends the browser to login.jsp with a session id it created")
                .isTrue();
        HttpResponse<String> login = get(consoleUri.resolve(bounce.group()).toString());
        assertThat(login.statusCode())
                .as("the console login page must render for the session the console created")
                .isEqualTo(200);
        assertThat(login.body())
                .as("the login page must be H2's own login form, which posts to login.do")
                .contains("login.do");
    }
}
