package com.example.catalog;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hidden eval assertions: the published security contract, verified purely
 * over HTTP. Public reads, authenticated writes, ADMIN area, HTTP Basic,
 * and no session cookies anywhere.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityContractEvalTest {

    private static final String PRODUCT_JSON = """
            {"name": "Laptop stand", "price": 49.00}
            """;

    @Autowired
    Environment environment;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(
                URI.create("http://localhost:" + environment.getProperty("local.server.port") + path));
    }

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes());
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void readingTheCatalogIsPublic() throws Exception {
        assertThat(send(request("/api/products").GET().build()).statusCode())
                .as("GET /api/products must be public").isEqualTo(200);
        assertThat(send(request("/api/products/1").GET().build()).statusCode())
                .as("GET /api/products/{id} must be public").isEqualTo(200);
    }

    @Test
    void anonymousWritesAreRejectedWith401() throws Exception {
        HttpResponse<String> response = send(request("/api/products")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(PRODUCT_JSON)).build());

        assertThat(response.statusCode())
                .as("anonymous POST must be 401, not a redirect to a login page")
                .isEqualTo(401);
    }

    @Test
    void authenticatedUserCanCreateProducts() throws Exception {
        HttpResponse<String> response = send(request("/api/products")
                .header("Content-Type", "application/json")
                .header("Authorization", basic("user", "user-pass"))
                .POST(HttpRequest.BodyPublishers.ofString(PRODUCT_JSON)).build());

        assertThat(response.statusCode()).isIn(200, 201);
        assertThat(response.body()).contains("Laptop stand");
    }

    @Test
    void adminAreaRequiresTheAdminRole() throws Exception {
        assertThat(send(request("/api/admin/stats").GET().build()).statusCode())
                .as("anonymous request to the admin area must be 401").isEqualTo(401);
        assertThat(send(request("/api/admin/stats")
                .header("Authorization", basic("user", "user-pass")).GET().build()).statusCode())
                .as("authenticated non-admin must be 403, not 401").isEqualTo(403);
        assertThat(send(request("/api/admin/stats")
                .header("Authorization", basic("admin", "admin-pass")).GET().build()).statusCode())
                .as("admin must reach the admin area").isEqualTo(200);
    }

    @Test
    void apiIsStatelessWithNoSessionCookies() throws Exception {
        HttpResponse<String> response = send(request("/api/products")
                .header("Content-Type", "application/json")
                .header("Authorization", basic("admin", "admin-pass"))
                .POST(HttpRequest.BodyPublishers.ofString(PRODUCT_JSON)).build());

        assertNoSessionCookie(response);
    }

    @Test
    void rejectedBrowserStyleRequestsCreateNoSessionEither() throws Exception {
        // The Accept header matters: a session-backed chain saves a rejected
        // HTML-accepting GET in a new session before it answers 401.
        HttpResponse<String> response = send(request("/api/admin/stats")
                .header("Accept", "text/html,application/xhtml+xml")
                .GET().build());

        assertThat(response.statusCode())
                .as("anonymous browser-style request to the admin area must be 401, not a login redirect")
                .isEqualTo(401);
        assertNoSessionCookie(response);
    }

    private static void assertNoSessionCookie(HttpResponse<String> response) {
        List<String> cookies = response.headers().allValues("Set-Cookie");
        assertThat(cookies)
                .as("a stateless API must not create sessions; got Set-Cookie: %s", cookies)
                .noneMatch(cookie -> cookie.toUpperCase().contains("JSESSIONID"));
    }
}
