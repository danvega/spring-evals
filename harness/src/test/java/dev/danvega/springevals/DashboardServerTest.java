package dev.danvega.springevals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The wizard API against a synthetic repo root; no Docker or agent CLI is needed for these paths. */
class DashboardServerTest {

    @TempDir
    Path root;

    private HttpServer server;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void startServer() throws Exception {
        Files.createDirectories(root.resolve("dashboard"));
        Files.writeString(root.resolve("dashboard/index.html"), "<p>ok</p>");
        Files.createDirectories(root.resolve("agents"));
        Files.createDirectories(root.resolve("evals"));
        Files.writeString(root.resolve("agents/alpha.json"), """
                {"name": "alpha", "provider": "claude", "model": "m-a",
                 "env": {"CLAUDE_CODE_OAUTH_TOKEN": "${ALPHA_TOKEN}"}, "estCostPerAttemptUsd": 1.5}
                """);
        Files.writeString(root.resolve("agents/beta.json"), """
                {"name": "beta", "provider": "gemini", "model": "m-b",
                 "env": {"GEMINI_API_KEY": "${BETA_KEY}"}}
                """);
        server = DashboardServer.start(root, 0);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void environmentReportsJavaDockerAndSelectionFile() throws Exception {
        Map<String, Object> body = get("/api/environment");
        assertEquals("docker", body.get("sandbox"));
        assertTrue(((Map<?, ?>) body.get("java")).containsKey("version"));
        assertTrue(((Map<?, ?>) body.get("docker")).containsKey("reachable"));
        assertEquals(SelectionConfig.FILE_NAME, body.get("selectionFile"));
        assertEquals(false, body.get("selectionFileExists"));
    }

    @Test
    void agentsListEveryConfigWithEnvReferencesAndNoRestrictionByDefault() throws Exception {
        Map<String, Object> body = get("/api/agents");
        assertEquals(false, body.get("restricts"));
        assertNull(body.get("selectionError"));
        List<?> agents = (List<?>) body.get("agents");
        assertEquals(2, agents.size());
        Map<?, ?> alpha = (Map<?, ?>) agents.get(0);
        assertEquals("alpha", alpha.get("name"));
        assertEquals(true, alpha.get("enabled"));
        assertEquals(List.of("ALPHA_TOKEN"), alpha.get("envRefs"));
        assertEquals(1.5, alpha.get("estCostPerAttemptUsd"));
        Map<?, ?> beta = (Map<?, ?>) agents.get(1);
        assertNull(beta.get("estCostPerAttemptUsd"));
    }

    @Test
    void enablingWritesTheLocalSelectionFileAndKeepsOtherKeys() throws Exception {
        Files.writeString(root.resolve(SelectionConfig.FILE_NAME), "{\"otherSetting\": true}");

        Map<String, Object> body = put("/api/agents/enabled", "{\"enabledAgents\": [\"beta\"]}", 200);

        assertEquals(true, body.get("restricts"));
        List<?> agents = (List<?>) body.get("agents");
        assertEquals(false, ((Map<?, ?>) agents.get(0)).get("enabled"));
        assertEquals(true, ((Map<?, ?>) agents.get(1)).get("enabled"));
        Map<String, Object> written = DashboardJson.readObject(root.resolve(SelectionConfig.FILE_NAME));
        assertEquals(true, written.get("otherSetting"));
        assertEquals(List.of("beta"), written.get("enabledAgents"));
        assertTrue(SelectionConfig.load(root, List.of("alpha", "beta")).restricts());
    }

    @Test
    void enablingNullRemovesTheRestriction() throws Exception {
        put("/api/agents/enabled", "{\"enabledAgents\": [\"alpha\"]}", 200);
        Map<String, Object> body = put("/api/agents/enabled", "{\"enabledAgents\": null}", 200);
        assertEquals(false, body.get("restricts"));
        assertFalse(DashboardJson.readObject(root.resolve(SelectionConfig.FILE_NAME)).containsKey("enabledAgents"));
    }

    @Test
    void unknownAgentNamesAreRefusedAndNothingIsWritten() throws Exception {
        Map<String, Object> body = put("/api/agents/enabled", "{\"enabledAgents\": [\"gamma\"]}", 400);
        assertTrue(String.valueOf(body.get("error")).contains("gamma"));
        assertFalse(Files.exists(root.resolve(SelectionConfig.FILE_NAME)));
    }

    @Test
    void mutationsWithoutTheApiHeaderAreRefused() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/agents/enabled"))
                .PUT(HttpRequest.BodyPublishers.ofString("{\"enabledAgents\": []}")).build();
        assertEquals(403, http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        HttpRequest validate = HttpRequest.newBuilder(uri("/api/validate?eval=boot/000"))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        assertEquals(403, http.send(validate, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void estimateMirrorsTheCliArithmetic() throws Exception {
        Files.createDirectories(root.resolve("evals/boot/000-a"));
        Files.createDirectories(root.resolve("evals/boot/001-b"));
        for (String dir : List.of("evals/boot/000-a", "evals/boot/001-b")) {
            Files.writeString(root.resolve(dir + "/eval.yaml"),
                    "name: " + dir.substring(dir.lastIndexOf('/') + 1) + "\nproject: boot\ntitle: t\ntype: fix\n"
                            + "difficulty: easy\ncategory: web\npilot: " + dir.endsWith("000-a") + "\n");
        }

        Map<String, Object> single = get("/api/estimate?agents=alpha&" + DashboardServer.ATTEMPTS_FLAG + "=1");
        assertEquals(2, single.get("evals"));
        assertEquals(DashboardServer.ATTEMPTS_FLAG, single.get("attemptsFlag"));
        assertEquals(3.0, single.get("totalWorst"));
        assertEquals(3.0, single.get("totalExpected"));

        Map<String, Object> retries = get("/api/estimate?agents=alpha,beta&" + DashboardServer.ATTEMPTS_FLAG + "=4");
        assertEquals(12.0, retries.get("totalWorst"));
        assertEquals(2 * 1.7 * 1.5, (double) retries.get("totalExpected"), 1e-9);
        assertEquals(List.of("beta"), retries.get("unknownCost"));

        Map<String, Object> pilot = get("/api/estimate?agents=alpha&pilot=true");
        assertEquals(List.of("boot/000-a"), pilot.get("evalIds"));
        assertEquals(400, status("/api/estimate?agents=alpha&" + DashboardServer.ATTEMPTS_FLAG + "=11"));
    }

    @Test
    void validateRejectsWrongMethodMissingEvalAndUnknownEval() throws Exception {
        assertEquals(405, status("/api/validate"));
        HttpRequest noEval = HttpRequest.newBuilder(uri("/api/validate")).header(DashboardServer.API_HEADER, "1")
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        assertEquals(400, http.send(noEval, HttpResponse.BodyHandlers.ofString()).statusCode());
        HttpRequest unknown = HttpRequest.newBuilder(uri("/api/validate?eval=boot/999-nope"))
                .header(DashboardServer.API_HEADER, "1").POST(HttpRequest.BodyPublishers.noBody()).build();
        assertEquals(400, http.send(unknown, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void staticFilesStillServe() throws Exception {
        assertEquals(200, status("/index.html"));
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private int status(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString())
                .statusCode();
    }

    private Map<String, Object> get(String path) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return DashboardJson.readObject(response.body());
    }

    private Map<String, Object> put(String path, String body, int expectedStatus) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).header(DashboardServer.API_HEADER, "1")
                .header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(expectedStatus, response.statusCode(), response.body());
        return DashboardJson.readObject(response.body());
    }
}
