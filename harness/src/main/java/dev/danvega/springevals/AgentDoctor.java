package dev.danvega.springevals;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import dev.danvega.springevals.Agents.AgentSpec;

/** Zero-generation readiness checks for configured agent CLIs and credentials. */
final class AgentDoctor {

    enum Level {
        READY, WARNING, BLOCKED
    }

    record Finding(Level level, String message) {
    }

    record Report(AgentSpec spec, List<Finding> findings) {
        Level level() {
            return findings.stream().map(Finding::level).max(Enum::compareTo).orElse(Level.READY);
        }
    }

    interface SystemAccess {
        String environment(String name);

        boolean fileExists(Path path);

        boolean executable(String command);

        CommandResult command(List<String> command);

        ProbeResult probeLocalModels(String baseUrl, String apiKey, String model);
    }

    record CommandResult(int exitCode, String output, boolean timedOut) {
    }

    record ProbeResult(boolean reachable, boolean modelPresent, String message) {
    }

    private static final Pattern ENV_REFERENCE = Pattern.compile("\\$\\{([A-Z0-9_]+)}");

    private final SystemAccess system;
    private final Map<String, CommandResult> commandCache = new HashMap<>();

    AgentDoctor() {
        this(new RealSystemAccess());
    }

    AgentDoctor(SystemAccess system) {
        this.system = system;
    }

    int print(List<AgentSpec> specs) {
        System.out.println("Agent configuration doctor (no model prompts or generation requests)\n");
        List<Report> reports = specs.stream().map(this::inspect).toList();
        for (Report report : reports) {
            System.out.printf("%-9s %s  (%s / %s)%n", report.level(), report.spec().name(),
                    report.spec().provider(), report.spec().model());
            for (Finding finding : report.findings()) {
                String marker = switch (finding.level()) {
                    case READY -> "✓";
                    case WARNING -> "!";
                    case BLOCKED -> "✗";
                };
                System.out.println("  " + marker + " " + finding.message());
            }
            System.out.println();
        }
        long ready = reports.stream().filter(r -> r.level() == Level.READY).count();
        long warnings = reports.stream().filter(r -> r.level() == Level.WARNING).count();
        long blocked = reports.stream().filter(r -> r.level() == Level.BLOCKED).count();
        System.out.printf("Summary: %d ready, %d warning, %d blocked.%n", ready, warnings, blocked);
        System.out.println("Credential presence is checked without printing values. Remote key validity is not tested.");
        return blocked == 0 ? 0 : 1;
    }

    Report inspect(AgentSpec spec) {
        List<Finding> findings = new ArrayList<>();
        String cli = cliCommand(spec.provider());
        if (!system.executable(cli)) {
            findings.add(new Finding(Level.BLOCKED, "required CLI not found on PATH: " + cli));
        } else {
            CommandResult version = command(List.of(cli, "--version"));
            if (version.exitCode() == 0 && !version.output().isBlank()) {
                findings.add(new Finding(Level.READY, "CLI available: " + firstLine(version.output())));
            } else {
                findings.add(new Finding(Level.WARNING, "CLI found but version could not be determined"));
            }
        }

        Map<String, String> resolved = resolveEnvironment(spec, findings);
        if (spec.estCostPerAttemptUsd() == null) {
            findings.add(new Finding(Level.BLOCKED,
                    "estCostPerAttemptUsd is missing; paid-run cost protection will refuse this config"));
        } else {
            findings.add(new Finding(Level.READY,
                    "configured estimate: $%.2f per attempt".formatted(spec.estCostPerAttemptUsd())));
        }
        if (spec.estCostPerAttemptUsd() != null && spec.estCostPerAttemptUsd() > 0
                && spec.provider().equals("claude") && spec.budgetUsd() == null) {
            findings.add(new Finding(Level.WARNING, "Claude per-attempt budgetUsd is not configured"));
        }

        boolean customEndpoint = resolved.containsKey("ANTHROPIC_BASE_URL") || resolved.containsKey("OPENAI_BASE_URL");
        boolean missingReferences = findings.stream().anyMatch(f -> f.level() == Level.BLOCKED
                && f.message().startsWith("missing environment variable"));
        if (!missingReferences) {
            checkAuthentication(spec, resolved, customEndpoint, findings);
            checkLocalEndpoint(resolved, findings);
        }
        checkContextContamination(spec, findings);
        return new Report(spec, List.copyOf(findings));
    }

    /**
     * Host-level agent context (global instruction files, user skills, MCP
     * config) can leak knowledge into benchmark runs and invalidate results.
     * The Claude CLI's SDK-mode default is to load no filesystem settings,
     * but the current adapter cannot force that, so it is surfaced as an
     * assumption to verify. Other CLIs read global context files the harness
     * cannot disable, so their presence is a warning.
     */
    private void checkContextContamination(AgentSpec spec, List<Finding> findings) {
        Path home = Path.of(System.getProperty("user.home"));
        switch (spec.provider()) {
            case "claude" -> findings.add(new Finding(Level.WARNING,
                    "context isolation relies on the Claude CLI SDK-mode default of loading no filesystem "
                            + "settings; the current adapter cannot force it, so verify once per CLI version "
                            + "before published campaigns"));
            case "codex" -> {
                if (system.fileExists(home.resolve(".codex/AGENTS.md"))) {
                    findings.add(new Finding(Level.WARNING,
                            "~/.codex/AGENTS.md exists and Codex loads it globally; move it aside for benchmark runs"));
                }
                if (system.fileExists(home.resolve(".codex/config.toml"))) {
                    findings.add(new Finding(Level.WARNING,
                            "~/.codex/config.toml exists; verify it defines no MCP servers or instructions before benchmark runs"));
                }
            }
            case "gemini" -> {
                if (system.fileExists(home.resolve(".gemini/GEMINI.md"))) {
                    findings.add(new Finding(Level.WARNING,
                            "~/.gemini/GEMINI.md exists and Gemini CLI loads it globally; move it aside for benchmark runs"));
                }
                if (system.fileExists(home.resolve(".gemini/settings.json"))) {
                    findings.add(new Finding(Level.WARNING,
                            "~/.gemini/settings.json exists; verify it defines no MCP servers before benchmark runs"));
                }
            }
            case "qwen-code" -> {
                if (system.fileExists(home.resolve(".qwen/QWEN.md"))) {
                    findings.add(new Finding(Level.WARNING,
                            "~/.qwen/QWEN.md exists and Qwen Code loads it globally; move it aside for benchmark runs"));
                }
                if (system.fileExists(home.resolve(".qwen/settings.json"))) {
                    findings.add(new Finding(Level.WARNING,
                            "~/.qwen/settings.json exists; verify it defines no MCP servers before benchmark runs"));
                }
            }
            default -> {
            }
        }
    }

    private Map<String, String> resolveEnvironment(AgentSpec spec, List<Finding> findings) {
        Map<String, String> resolved = new HashMap<>();
        for (var entry : spec.env().entrySet()) {
            String value = entry.getValue();
            var matcher = ENV_REFERENCE.matcher(value);
            StringBuffer expanded = new StringBuffer();
            boolean missing = false;
            while (matcher.find()) {
                String variable = matcher.group(1);
                String hostValue = system.environment(variable);
                if (hostValue == null || hostValue.isBlank()) {
                    findings.add(new Finding(Level.BLOCKED,
                            "missing environment variable required by config: " + variable));
                    missing = true;
                    hostValue = "";
                } else {
                    findings.add(new Finding(Level.READY, "required environment variable is set: " + variable));
                }
                matcher.appendReplacement(expanded, java.util.regex.Matcher.quoteReplacement(hostValue));
            }
            matcher.appendTail(expanded);
            if (!missing) {
                resolved.put(entry.getKey(), expanded.toString());
            }
        }
        return resolved;
    }

    private void checkAuthentication(AgentSpec spec, Map<String, String> env, boolean customEndpoint,
            List<Finding> findings) {
        if (customEndpoint) {
            String credential = env.getOrDefault("ANTHROPIC_API_KEY", env.get("OPENAI_API_KEY"));
            findings.add(credential == null || credential.isBlank()
                    ? new Finding(Level.BLOCKED, "custom endpoint has no configured API credential")
                    : new Finding(Level.READY, "custom endpoint credential is configured"));
            return;
        }
        switch (spec.provider()) {
            case "claude" -> {
                if (!system.executable("claude")) {
                    return;
                }
                CommandResult status = command(List.of("claude", "auth", "status"));
                if (!status.timedOut() && status.exitCode() == 0 && status.output().contains("\"loggedIn\": true")) {
                    findings.add(new Finding(Level.READY, "Claude authentication status reports logged in"));
                } else {
                    findings.add(new Finding(Level.BLOCKED, "Claude authentication status does not report logged in"));
                }
            }
            case "codex" -> {
                boolean envKey = present(system.environment("OPENAI_API_KEY"));
                boolean authFile = system.fileExists(Path.of(System.getProperty("user.home"), ".codex", "auth.json"));
                if (envKey || authFile) {
                    findings.add(new Finding(Level.READY, envKey
                            ? "OPENAI_API_KEY is set" : "Codex credential file is present"));
                    findings.add(new Finding(Level.WARNING,
                            "this Codex CLI exposes no non-generative login-status command; credential validity is unverified"));
                } else {
                    findings.add(new Finding(Level.BLOCKED,
                            "no OPENAI_API_KEY or ~/.codex/auth.json credential source found"));
                }
            }
            case "gemini" -> {
                boolean key = present(system.environment("GEMINI_API_KEY"))
                        || present(system.environment("GOOGLE_API_KEY"));
                boolean oauth = system.fileExists(Path.of(System.getProperty("user.home"), ".gemini", "oauth_creds.json"))
                        || system.fileExists(Path.of(System.getProperty("user.home"), ".config", "gemini", "oauth_creds.json"));
                if (key || oauth) {
                    findings.add(new Finding(Level.READY, key
                            ? "Gemini API-key environment is configured" : "Gemini OAuth credential file is present"));
                } else {
                    findings.add(new Finding(Level.BLOCKED,
                            "no GEMINI_API_KEY, GOOGLE_API_KEY, or Gemini OAuth credential file found"));
                }
            }
            case "qwen-code" -> findings.add(new Finding(Level.BLOCKED,
                    "qwen-code requires an OPENAI-compatible endpoint configuration"));
            default -> findings.add(new Finding(Level.WARNING, "authentication check unavailable for provider"));
        }
    }

    private void checkLocalEndpoint(Map<String, String> env, List<Finding> findings) {
        String baseUrl = env.getOrDefault("OPENAI_BASE_URL", env.get("ANTHROPIC_BASE_URL"));
        if (baseUrl == null || !isLocal(baseUrl)) {
            return;
        }
        String model = env.getOrDefault("OPENAI_MODEL", "");
        ProbeResult probe = system.probeLocalModels(baseUrl, env.get("OPENAI_API_KEY"), model);
        if (!probe.reachable()) {
            findings.add(new Finding(Level.BLOCKED, probe.message()));
        } else if (!model.isBlank() && !probe.modelPresent()) {
            findings.add(new Finding(Level.BLOCKED, probe.message()));
        } else {
            findings.add(new Finding(Level.READY, probe.message()));
        }
    }

    private static String cliCommand(String provider) {
        return switch (provider) {
            case "claude" -> "claude";
            case "codex" -> "codex";
            case "gemini" -> "gemini";
            case "qwen-code" -> "qwen";
            default -> provider;
        };
    }

    private CommandResult command(List<String> command) {
        return commandCache.computeIfAbsent(String.join("\u0000", command), ignored -> system.command(command));
    }

    private static boolean isLocal(String value) {
        try {
            String host = URI.create(value).getHost();
            return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstLine(String value) {
        return value.strip().lines().findFirst().orElse("unknown");
    }

    private static final class RealSystemAccess implements SystemAccess {

        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

        @Override
        public String environment(String name) {
            return System.getenv(name);
        }

        @Override
        public boolean fileExists(Path path) {
            return Files.isRegularFile(path);
        }

        @Override
        public boolean executable(String command) {
            String path = System.getenv("PATH");
            if (path == null) {
                return false;
            }
            for (String directory : path.split(Pattern.quote(System.getProperty("path.separator")))) {
                if (Files.isExecutable(Path.of(directory, command))) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public CommandResult command(List<String> command) {
            try {
                Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
                if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    return new CommandResult(-1, "", true);
                }
                return new CommandResult(process.exitValue(),
                        new String(process.getInputStream().readAllBytes()).strip(), false);
            } catch (IOException e) {
                return new CommandResult(-1, "", false);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new CommandResult(-1, "", true);
            }
        }

        @Override
        public ProbeResult probeLocalModels(String baseUrl, String apiKey, String model) {
            try {
                String modelsUrl = baseUrl.replaceAll("/+$", "") + "/models";
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(modelsUrl))
                        .timeout(Duration.ofSeconds(3)).GET();
                if (present(apiKey)) {
                    request.header("Authorization", "Bearer " + apiKey);
                }
                HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    return new ProbeResult(false, false,
                            "local endpoint probe returned HTTP " + response.statusCode() + ": " + modelsUrl);
                }
                boolean modelPresent = model.isBlank() || response.body().contains("\"" + model + "\"")
                        || response.body().contains(model);
                return new ProbeResult(true, modelPresent, modelPresent
                        ? "local endpoint is reachable" + (model.isBlank() ? "" : " and model is available: " + model)
                        : "local endpoint is reachable but model is not available: " + model);
            } catch (Exception e) {
                return new ProbeResult(false, false, "local endpoint is not reachable: " + baseUrl);
            }
        }
    }
}
