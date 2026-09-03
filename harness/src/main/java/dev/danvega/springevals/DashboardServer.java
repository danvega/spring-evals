package dev.danvega.springevals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;

import dev.danvega.springevals.Agents.AgentSpec;
import dev.danvega.springevals.cli.Finding;

/**
 * Static dashboard plus the local JSON API behind dashboard/onboarding.html.
 * Kept out of Main so UI conveniences never rotate the benchmark identity hash.
 * Nothing here can start a paid run; the wizard only prints the run command.
 */
final class DashboardServer {

    /** The attempts selector the wizard prints; H2 renames it to samples in one place. */
    static final String ATTEMPTS_FLAG = "attempts";

    /** Mutating endpoints require this header so a cross-site page cannot reach them without a preflight. */
    static final String API_HEADER = "X-Spring-Evals";

    private static final Pattern ENV_REFERENCE = Pattern.compile("\\$\\{([A-Z0-9_]+)}");
    private static final double EXPECTED_ATTEMPTS_WITH_RETRIES = 1.7;

    private final Path root;
    private final EvalCatalog catalog;
    private final Agents agents;
    private final Semaphore validateLock = new Semaphore(1);

    private DashboardServer(Path root) {
        this.root = root;
        this.catalog = new EvalCatalog(root);
        this.agents = new Agents(root);
    }

    static void serve(Path repoRoot, Map<String, String> opts) throws Exception {
        int port = Integer.parseInt(opts.getOrDefault("port", "4173"));
        HttpServer server = start(repoRoot, port);
        System.out.println("Dashboard: http://localhost:" + server.getAddress().getPort() + "  (Ctrl+C to stop)");
        System.out.println("Onboarding: http://localhost:" + server.getAddress().getPort() + "/onboarding.html");
        Thread.currentThread().join();
    }

    /** Loopback only: the API writes local config and launches local processes. */
    static HttpServer start(Path repoRoot, int port) throws IOException {
        Path dashboard = repoRoot.resolve("dashboard").toRealPath();
        HttpServer server = SimpleFileServer.createFileServer(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), port), dashboard,
                SimpleFileServer.OutputLevel.INFO);
        DashboardServer api = new DashboardServer(repoRoot);
        server.createContext("/api/environment", exchange -> api.handle(exchange, "GET", api::environment));
        server.createContext("/api/agents/enabled", exchange -> api.handle(exchange, "PUT", api::updateEnabled));
        server.createContext("/api/agents", exchange -> api.handle(exchange, "GET", api::agents));
        server.createContext("/api/doctor", exchange -> api.handle(exchange, "GET", api::doctor));
        server.createContext("/api/evals", exchange -> api.handle(exchange, "GET", api::evals));
        server.createContext("/api/estimate", exchange -> api.handle(exchange, "GET", api::estimate));
        server.createContext("/api/validate", api::validate);
        // Handler threads must see the harness classpath, or ServiceLoader finds no AgentCli under exec:java.
        ClassLoader loader = DashboardServer.class.getClassLoader();
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(task -> executor.execute(() -> {
            Thread.currentThread().setContextClassLoader(loader);
            task.run();
        }));
        server.start();
        return server;
    }

    @FunctionalInterface
    private interface JsonEndpoint {
        Object handle(HttpExchange exchange) throws IOException;
    }

    /** The exchange is closed only after the error branches have written their response. */
    private void handle(HttpExchange exchange, String method, JsonEndpoint endpoint) throws IOException {
        try {
            if (!exchange.getRequestMethod().equalsIgnoreCase(method)) {
                respond(exchange, 405, error("use " + method));
                return;
            }
            if (!method.equals("GET") && exchange.getRequestHeaders().getFirst(API_HEADER) == null) {
                respond(exchange, 403, error("missing " + API_HEADER + " header"));
                return;
            }
            respond(exchange, 200, endpoint.handle(exchange));
        } catch (IllegalArgumentException | IllegalStateException e) {
            respond(exchange, 400, error(e.getMessage()));
        } catch (RuntimeException e) {
            respond(exchange, 500, error(e.toString()));
        } finally {
            exchange.close();
        }
    }

    Map<String, Object> environment(HttpExchange exchange) {
        Map<String, Object> java = new LinkedHashMap<>();
        java.put("version", String.valueOf(Runtime.version().feature()));
        java.put("runtime", Runtime.version().toString());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("java", java);
        body.put("docker", dockerStatus());
        body.put("sandbox", "docker");
        body.put("repoRoot", root.toString());
        body.put("selectionFile", SelectionConfig.FILE_NAME);
        body.put("selectionFileExists", Files.exists(root.resolve(SelectionConfig.FILE_NAME)));
        body.put("proveEval", defaultProveEval());
        return body;
    }

    private Map<String, Object> dockerStatus() {
        boolean reachable = DockerSandbox.dockerAvailable();
        String image = DockerSandbox.imageTag(root);
        Map<String, Object> docker = new LinkedHashMap<>();
        docker.put("reachable", reachable);
        docker.put("image", image);
        docker.put("imageBuilt", reachable && DockerSandbox.imageExists(image));
        return docker;
    }

    private String defaultProveEval() {
        List<EvalDefinition> all = catalog.all();
        return all.stream().map(EvalDefinition::id)
                .filter(id -> id.equals("boot/002-restclient-migration")).findFirst()
                .orElse(all.isEmpty() ? null : all.getFirst().id());
    }

    Map<String, Object> agents(HttpExchange exchange) {
        List<String> names = agents.names();
        SelectionConfig selection = null;
        String selectionError = null;
        try {
            selection = SelectionConfig.load(root, names);
        } catch (IllegalArgumentException e) {
            selectionError = e.getMessage();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String name : names) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            try {
                AgentSpec spec = agents.load(name);
                row.put("provider", spec.provider());
                row.put("model", spec.model());
                row.put("estCostPerAttemptUsd", spec.estCostPerAttemptUsd());
                row.put("envRefs", envReferences(spec));
                row.put("configError", null);
            } catch (RuntimeException e) {
                row.put("configError", e.getMessage());
            }
            row.put("enabled", selection == null || selection.enabled(name));
            rows.add(row);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("selectionFile", SelectionConfig.FILE_NAME);
        body.put("restricts", selection != null && selection.restricts());
        body.put("selectionError", selectionError);
        body.put("agents", rows);
        return body;
    }

    private static List<String> envReferences(AgentSpec spec) {
        Set<String> refs = new LinkedHashSet<>();
        for (String value : spec.env().values()) {
            Matcher matcher = ENV_REFERENCE.matcher(value);
            while (matcher.find()) {
                refs.add(matcher.group(1));
            }
        }
        return List.copyOf(refs);
    }

    /** Body {"enabledAgents": [names]} restricts; {"enabledAgents": null} removes the restriction. */
    Map<String, Object> updateEnabled(HttpExchange exchange) throws IOException {
        Map<String, Object> request = DashboardJson.readObject(
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        if (!request.containsKey("enabledAgents")) {
            throw new IllegalArgumentException("body must carry enabledAgents (an array of names, or null)");
        }
        Path file = root.resolve(SelectionConfig.FILE_NAME);
        Map<String, Object> config = DashboardJson.readObject(file);
        Object requested = request.get("enabledAgents");
        if (requested == null) {
            config.remove("enabledAgents");
        } else {
            List<Object> values = DashboardJson.list(requested);
            if (values == null) {
                throw new IllegalArgumentException("enabledAgents must be an array of agent names");
            }
            List<String> known = agents.names();
            List<String> names = new ArrayList<>();
            for (Object value : values) {
                if (!(value instanceof String name) || !known.contains(name)) {
                    throw new IllegalArgumentException("unknown agent '" + value + "' (no agents/" + value + ".json)");
                }
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
            config.put("enabledAgents", names);
        }
        DashboardJson.writeObject(file, config);
        return agents(exchange);
    }

    Map<String, Object> doctor(HttpExchange exchange) {
        Map<String, String> query = query(exchange);
        List<String> names = query.containsKey("agent")
                ? List.of(query.get("agent").split(","))
                : agents.names();
        List<AgentSpec> specs = new ArrayList<>();
        List<Map<String, Object>> invalid = new ArrayList<>();
        for (String name : names) {
            try {
                specs.add(agents.load(name));
            } catch (RuntimeException e) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", name);
                row.put("status", Finding.Level.BLOCKED.name());
                row.put("enabled", true);
                row.put("findings", List.of(Map.of("level", Finding.Level.BLOCKED.name(),
                        "text", "invalid agent config: " + e.getMessage())));
                invalid.add(row);
            }
        }
        SelectionConfig selection = SelectionConfig.load(root, agents.names());
        Set<String> excluded = new LinkedHashSet<>();
        specs.stream().map(AgentSpec::name).filter(name -> !selection.enabled(name)).forEach(excluded::add);
        Map<String, Object> body = new AgentDoctor().json(specs, excluded);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) body.get("agents");
        rows.addAll(invalid);
        body.put("docker", dockerStatus());
        return body;
    }

    List<Map<String, Object>> evals(HttpExchange exchange) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EvalDefinition eval : catalog.all()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", eval.id());
            row.put("project", eval.project());
            row.put("title", eval.title());
            row.put("type", eval.meta().get("type"));
            row.put("difficulty", eval.meta().get("difficulty"));
            row.put("category", eval.meta().get("category"));
            row.put("pilot", Boolean.parseBoolean(eval.meta().getOrDefault("pilot", "false")));
            rows.add(row);
        }
        return rows;
    }

    /** Same arithmetic as ./spring-evals estimate: expected assumes about 1.7 attempts per eval when retries are allowed. */
    Map<String, Object> estimate(HttpExchange exchange) {
        Map<String, String> query = query(exchange);
        int attempts = Integer.parseInt(query.getOrDefault(ATTEMPTS_FLAG, query.getOrDefault("samples", "1")));
        if (attempts < 1 || attempts > 10) {
            throw new IllegalArgumentException(ATTEMPTS_FLAG + " must be between 1 and 10");
        }
        List<EvalDefinition> targets = selectEvals(query);
        List<AgentSpec> specs = selectAgents(query);
        double expectedAttempts = attempts == 1 ? 1.0 : EXPECTED_ATTEMPTS_WITH_RETRIES;
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> unknownCost = new ArrayList<>();
        double totalExpected = 0;
        double totalWorst = 0;
        for (AgentSpec spec : specs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("agent", spec.name());
            if (spec.estCostPerAttemptUsd() == null) {
                unknownCost.add(spec.name());
                row.put("perAttempt", null);
                row.put("expected", null);
                row.put("worst", null);
            } else {
                double perAttempt = spec.estCostPerAttemptUsd();
                double expected = targets.size() * expectedAttempts * perAttempt;
                double worst = targets.size() * attempts * perAttempt;
                totalExpected += expected;
                totalWorst += worst;
                row.put("perAttempt", perAttempt);
                row.put("expected", expected);
                row.put("worst", worst);
            }
            rows.add(row);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("evals", targets.size());
        body.put("evalIds", targets.stream().map(EvalDefinition::id).toList());
        body.put(ATTEMPTS_FLAG, attempts);
        body.put("attemptsFlag", ATTEMPTS_FLAG);
        body.put("rows", rows);
        body.put("totalExpected", totalExpected);
        body.put("totalWorst", totalWorst);
        body.put("unknownCost", unknownCost);
        return body;
    }

    private List<EvalDefinition> selectEvals(Map<String, String> query) {
        if (query.containsKey("eval")) {
            return List.of(query.get("eval").split(",")).stream().map(catalog::load).toList();
        }
        List<EvalDefinition> all = catalog.all();
        if (query.containsKey("project")) {
            return all.stream().filter(eval -> eval.project().equals(query.get("project"))).toList();
        }
        if ("true".equals(query.get("pilot"))) {
            return all.stream().filter(eval -> Boolean.parseBoolean(eval.meta().getOrDefault("pilot", "false")))
                    .toList();
        }
        return all;
    }

    private List<AgentSpec> selectAgents(Map<String, String> query) {
        if (query.containsKey("agents")) {
            return List.of(query.get("agents").split(",")).stream().map(agents::load).toList();
        }
        SelectionConfig selection = SelectionConfig.load(root, agents.names());
        return agents.loadAll().stream().filter(spec -> selection.enabled(spec.name())).toList();
    }

    /** Streams ./spring-evals validate for one eval; a second concurrent run is refused. */
    void validate(HttpExchange exchange) throws IOException {
        try {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                respond(exchange, 405, error("use POST"));
                return;
            }
            if (exchange.getRequestHeaders().getFirst(API_HEADER) == null) {
                respond(exchange, 403, error("missing " + API_HEADER + " header"));
                return;
            }
            String id = query(exchange).get("eval");
            if (id == null || id.isBlank()) {
                respond(exchange, 400, error("eval query parameter is required"));
                return;
            }
            try {
                catalog.load(id);
            } catch (IllegalArgumentException e) {
                respond(exchange, 400, error(e.getMessage()));
                return;
            }
            if (!validateLock.tryAcquire()) {
                respond(exchange, 409, error("a validate is already running"));
                return;
            }
            try {
                stream(exchange, List.of(root.resolve("spring-evals").toString(), "validate", id));
            } finally {
                validateLock.release();
            }
        } finally {
            exchange.close();
        }
    }

    private void stream(HttpExchange exchange, List<String> command) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, 0);
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        try (OutputStream out = exchange.getResponseBody();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            out.write(("$ " + String.join(" ", command) + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            String line;
            while ((line = reader.readLine()) != null) {
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            int exit = process.waitFor();
            out.write(("[exit " + exit + "]\n").getBytes(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> params = new LinkedHashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isBlank()) {
            return params;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "true" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return body;
    }

    private static void respond(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = DashboardJson.write(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
