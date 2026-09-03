package dev.danvega.springevals;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
        put("/api/agents/enabled", "{\"enabledAgents\": [\"../alpha\"]}", 400);
        assertFalse(Files.exists(root.resolve(SelectionConfig.FILE_NAME)));
    }

    @Test
    void malformedBodiesAreArgumentErrorsNotServerFaults() throws Exception {
        assertTrue(String.valueOf(put("/api/agents/enabled", "not json", 400).get("error")).contains("JSON"));
        put("/api/agents/enabled", "[\"alpha\"]", 400);
        put("/api/agents/enabled", "{\"enabledAgents\": \"alpha\"}", 400);
        put("/api/agents/enabled", "{\"somethingElse\": 1}", 400);
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
    void foreignHostHeadersAreRefusedSoDnsRebindingCannotReachTheApi() throws Exception {
        int port = server.getAddress().getPort();
        assertEquals(403, rawStatus("GET", "/api/environment", "evil.example:" + port));
        assertEquals(403, rawStatus("GET", "/api/agents", "localhost:" + (port + 1)));
        assertEquals(403, rawStatus("POST", "/api/validate?eval=x", "127.0.0.1.evil.example:" + port));
        assertEquals(200, rawStatus("GET", "/api/agents", "localhost:" + port));
        assertEquals(200, rawStatus("GET", "/api/agents", "127.0.0.1:" + port));
        assertEquals(200, rawStatus("GET", "/api/agents", "[::1]:" + port));
    }

    @Test
    void estimateMirrorsTheCliArithmetic() throws Exception {
        writeEval("boot/000-a", true);
        writeEval("boot/001-b", false);

        Map<String, Object> single = get("/api/estimate?agents=alpha&" + DashboardServer.SAMPLES_FLAG + "=1");
        assertEquals(2, single.get("evals"));
        assertEquals(DashboardServer.SAMPLES_FLAG, single.get("samplesFlag"));
        assertEquals(3.0, single.get("total"));

        Map<String, Object> three = get("/api/estimate?agents=alpha,beta&" + DashboardServer.SAMPLES_FLAG + "=3");
        assertEquals(9.0, three.get("total"));
        assertEquals(List.of("beta"), three.get("unknownCost"));
        Map<?, ?> alphaRow = (Map<?, ?>) ((List<?>) three.get("rows")).get(0);
        assertEquals(6, alphaRow.get(DashboardServer.SAMPLES_FLAG));
        assertEquals(9.0, alphaRow.get("projected"));

        Map<String, Object> defaults = get("/api/estimate?agents=alpha");
        assertEquals(DashboardServer.DEFAULT_SAMPLES, defaults.get(DashboardServer.SAMPLES_FLAG));

        Map<String, Object> pilot = get("/api/estimate?agents=alpha&pilot=true");
        assertEquals(List.of("boot/000-a"), pilot.get("evalIds"));
        assertEquals(400, status("/api/estimate?agents=alpha&" + DashboardServer.SAMPLES_FLAG + "=11"));
        assertEquals(400, status("/api/estimate?agents=alpha&attempts=1"));
    }

    @Test
    void onlyCatalogEvalIdsAndListedAgentNamesAreAccepted() throws Exception {
        writeEval("boot/000-a", false);
        Files.createDirectories(root.resolve("outside/999-x"));
        Files.writeString(root.resolve("outside/999-x/eval.yaml"), "name: 999-x\nproject: outside\n");

        assertEquals(200, status("/api/estimate?agents=alpha&eval=boot/000-a"));
        assertEquals(400, status("/api/estimate?agents=alpha&eval=../outside/999-x"));
        assertEquals(400, status("/api/estimate?agents=alpha&eval=" + root.resolve("outside/999-x")));
        assertEquals(400, status("/api/estimate?agents=../agents/alpha"));
        assertEquals(400, status("/api/estimate?agents=gamma"));
        assertEquals(200, status("/api/doctor?agent=alpha"));
        assertEquals(400, status("/api/doctor?agent=../agents/alpha"));
        HttpRequest escape = HttpRequest.newBuilder(uri("/api/validate?eval=../outside/999-x"))
                .header(DashboardServer.API_HEADER, "1").POST(HttpRequest.BodyPublishers.noBody()).build();
        assertEquals(400, http.send(escape, HttpResponse.BodyHandlers.ofString()).statusCode());
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

    private void writeEval(String id, boolean pilot) throws Exception {
        Path dir = root.resolve("evals").resolve(id);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("eval.yaml"), "name: " + id.substring(id.indexOf('/') + 1)
                + "\nproject: boot\ntitle: t\ntype: fix\ndifficulty: easy\ncategory: web\npilot: " + pilot + "\n");
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private int status(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString())
                .statusCode();
    }

    /** The JDK client refuses to spoof Host, so the rebinding check speaks HTTP over a plain socket. */
    private int rawStatus(String method, String path, String host) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.getAddress().getPort())) {
            OutputStream out = socket.getOutputStream();
            out.write((method + " " + path + " HTTP/1.1\r\nHost: " + host + "\r\n" + DashboardServer.API_HEADER
                    + ": 1\r\nConnection: close\r\nContent-Length: 0\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                    StandardCharsets.UTF_8));
            String statusLine = reader.readLine();
            return Integer.parseInt(statusLine.split(" ")[1]);
        }
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
