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

        /** File text for auth-mode detection, or null when unreadable. Values are never printed. */
        String fileContent(Path path);

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
            System.out.printf("%-9s %s  (%s / %s)%s%n", report.level(), report.spec().name(),
                    report.spec().provider(), report.spec().model(),
                    report.spec().enabled() ? "" : "  [disabled: skipped by --all-agents and --family]");
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
        if (specs.stream().anyMatch(spec -> spec.provider().equals("claude"))) {
            System.out.println("Claude-family note: runs are isolated via CLAUDE_CONFIG_DIR (an empty config dir), "
                    + "so host CLAUDE.md, skills, and MCP servers cannot load. Authenticate with "
                    + "CLAUDE_CODE_OAUTH_TOKEN from `claude setup-token` (subscription) or ANTHROPIC_API_KEY "
                    + "(metered API); interactive login does not apply inside the sterile config.");
        }
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
     * Claude isolation is enforced by the harness: every attempt runs with a
     * fresh empty CLAUDE_CONFIG_DIR. Other CLIs read global context files the
     * harness cannot disable, so their presence is a warning.
     */
    private void checkContextContamination(AgentSpec spec, List<Finding> findings) {
        Path home = Path.of(System.getProperty("user.home"));
        switch (spec.provider()) {
            // The Claude isolation assumption is a per-CLI-version advisory, not a
            // per-agent problem, so it prints once in the summary instead of here.
            case "codex" -> {
                if (system.fileExists(home.resolve(".codex/AGENTS.md"))) {
                    findings.add(new Finding(Level.WARNING,
                            "~/.codex/AGENTS.md exists and Codex loads it globally; move it aside for benchmark runs"));
                }
                String codexToml = system.fileContent(home.resolve(".codex/config.toml"));
                if (codexToml != null && (codexToml.contains("mcp_servers") || codexToml.contains("instructions"))) {
                    findings.add(new Finding(Level.WARNING,
                            "~/.codex/config.toml defines MCP servers or instructions; remove them for benchmark runs"));
                }
            }
            case "gemini" -> {
                if (system.fileExists(home.resolve(".gemini/GEMINI.md"))) {
                    findings.add(new Finding(Level.WARNING,
                            "~/.gemini/GEMINI.md exists and Gemini CLI loads it globally; move it aside for benchmark runs"));
                }
                String geminiSettings = system.fileContent(home.resolve(".gemini/settings.json"));
                if (geminiSettings != null && geminiSettings.contains("\"mcpServers\"")) {
                    findings.add(new Finding(Level.WARNING,
                            "~/.gemini/settings.json defines MCP servers; remove them for benchmark runs"));
                }
            }
            case "qwen-code" -> {
                if (system.fileExists(home.resolve(".qwen/QWEN.md"))) {
                    findings.add(new Finding(Level.WARNING,
                            "~/.qwen/QWEN.md exists and Qwen Code loads it globally; move it aside for benchmark runs"));
                }
                String qwenSettings = system.fileContent(home.resolve(".qwen/settings.json"));
                if (qwenSettings != null && qwenSettings.contains("\"mcpServers\"")) {
                    findings.add(new Finding(Level.WARNING,
                            "~/.qwen/settings.json defines MCP servers; remove them for benchmark runs"));
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
                // Benchmark runs use an isolated CLAUDE_CONFIG_DIR where the
                // interactive subscription login does not apply. Two working
                // credentials: a long-lived subscription token from
                // `claude setup-token` (CLAUDE_CODE_OAUTH_TOKEN, draws on the
                // plan) or ANTHROPIC_API_KEY (metered API billing). Either may
                // come from the agent config's env (the recommended
                // benchmark-scoped pattern) or the host environment.
                boolean oauthToken = present(env.get("CLAUDE_CODE_OAUTH_TOKEN"))
                        || present(system.environment("CLAUDE_CODE_OAUTH_TOKEN"));
                boolean apiKey = present(env.get("ANTHROPIC_API_KEY"))
                        || present(system.environment("ANTHROPIC_API_KEY"));
                if (oauthToken) {
                    findings.add(new Finding(Level.READY,
                            "billing: subscription token from `claude setup-token` (draws on the Claude plan; "
                                    + "works inside the isolated config dir)"));
                    if (present(env.get("ANTHROPIC_API_KEY"))) {
                        // Only the agent config's own env survives the run's
                        // host-var stripping, so a host-level API key is not a
                        // billing hazard; both credentials in the config are.
                        findings.add(new Finding(Level.WARNING,
                                "the agent config declares both CLAUDE_CODE_OAUTH_TOKEN and ANTHROPIC_API_KEY; "
                                        + "the CLI prefers the API key, so billing would be metered API"));
                    }
                } else if (apiKey) {
                    findings.add(new Finding(Level.READY,
                            "billing: ANTHROPIC_API_KEY (metered API; runs use an isolated Claude config dir "
                                    + "where interactive subscription login does not apply)"));
                } else {
                    findings.add(new Finding(Level.BLOCKED,
                            "benchmark runs use an isolated Claude config dir; interactive login does not carry "
                                    + "into it. Set CLAUDE_CODE_OAUTH_TOKEN (from `claude setup-token`, subscription) "
                                    + "or ANTHROPIC_API_KEY (metered API)"));
                }
            }
            case "codex" -> {
                boolean envKey = present(system.environment("OPENAI_API_KEY"));
                Path authFile = Path.of(System.getProperty("user.home"), ".codex", "auth.json");
                String auth = system.fileExists(authFile) ? system.fileContent(authFile) : null;
                boolean chatgptLogin = auth != null && auth.contains("\"tokens\"")
                        && Pattern.compile("\"OPENAI_API_KEY\"\\s*:\\s*null").matcher(auth).find();
                boolean fileKey = auth != null && Pattern.compile("\"OPENAI_API_KEY\"\\s*:\\s*\"").matcher(auth).find();
                String codexConfig = system.fileContent(
                        Path.of(System.getProperty("user.home"), ".codex", "config.toml"));
                String pinnedMethod = codexConfig == null ? null
                        : jsonlessTomlValue(codexConfig, "preferred_auth_method");
                if (!envKey && auth == null) {
                    findings.add(new Finding(Level.BLOCKED,
                            "no OPENAI_API_KEY or ~/.codex/auth.json credential source found"));
                } else {
                    if (pinnedMethod != null) {
                        findings.add(new Finding(Level.READY, "billing: pinned to " + pinnedMethod
                                + " by preferred_auth_method in ~/.codex/config.toml"));
                    } else if (chatgptLogin && envKey) {
                        findings.add(new Finding(Level.WARNING,
                                "both a ChatGPT sign-in and OPENAI_API_KEY are present; set preferred_auth_method in "
                                        + "~/.codex/config.toml to control which one bills"));
                    } else if (chatgptLogin) {
                        findings.add(new Finding(Level.READY,
                                "billing: ChatGPT sign-in (subscription covers usage)"));
                    } else if (envKey || fileKey) {
                        findings.add(new Finding(Level.READY,
                                "billing: OpenAI API key (metered API billing, not a subscription)"));
                    } else {
                        findings.add(new Finding(Level.READY, "Codex credential file is present"));
                    }
                    findings.add(new Finding(Level.WARNING,
                            "this Codex CLI exposes no non-generative login-status command; credential validity is unverified"));
                }
            }
            case "gemini" -> {
                boolean key = present(system.environment("GEMINI_API_KEY"))
                        || present(system.environment("GOOGLE_API_KEY"));
                boolean oauth = system.fileExists(Path.of(System.getProperty("user.home"), ".gemini", "oauth_creds.json"))
                        || system.fileExists(Path.of(System.getProperty("user.home"), ".config", "gemini", "oauth_creds.json"));
                if (!key && !oauth) {
                    findings.add(new Finding(Level.BLOCKED,
                            "no GEMINI_API_KEY, GOOGLE_API_KEY, or Gemini OAuth credential file found"));
                } else {
                    String settings = system.fileContent(
                            Path.of(System.getProperty("user.home"), ".gemini", "settings.json"));
                    String selected = settings == null ? null : jsonString(settings, "selectedAuthType");
                    if ("oauth-personal".equals(selected) && oauth) {
                        findings.add(new Finding(Level.READY,
                                "billing: Google account sign-in (plan or free Code Assist quota)"));
                        if (!present(system.environment("GOOGLE_CLOUD_PROJECT"))) {
                            findings.add(new Finding(Level.WARNING,
                                    "some Google accounts require GOOGLE_CLOUD_PROJECT for non-interactive runs; "
                                            + "if runs fail with a project error, set it or use GEMINI_API_KEY instead"));
                        }
                    } else if (selected != null && selected.contains("api-key") && key) {
                        findings.add(new Finding(Level.READY,
                                "billing: Gemini API key (metered or AI Studio free tier)"));
                    } else if (key && oauth) {
                        findings.add(new Finding(Level.WARNING,
                                "both a Google sign-in and an API key are present; run the CLI's /auth to pick one "
                                        + "so the billing source is explicit"));
                    } else {
                        findings.add(new Finding(Level.READY, key
                                ? "billing: Gemini API key (metered or AI Studio free tier)"
                                : "billing: Google account sign-in (plan or free Code Assist quota)"));
                        if (!key && !present(system.environment("GOOGLE_CLOUD_PROJECT"))) {
                            findings.add(new Finding(Level.WARNING,
                                    "some Google accounts require GOOGLE_CLOUD_PROJECT for non-interactive runs; "
                                            + "if runs fail with a project error, set it or use GEMINI_API_KEY instead"));
                        }
                    }
                }
            }
            case "qwen-code" -> findings.add(new Finding(Level.BLOCKED,
                    "qwen-code requires an OPENAI-compatible endpoint configuration"));
            default -> findings.add(new Finding(Level.WARNING, "authentication check unavailable for provider"));
        }
    }

    /** Minimal TOML value lookup: key = "value" on its own line. */
    private static String jsonlessTomlValue(String toml, String key) {
        var matcher = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*=\\s*\"([^\"]+)\"").matcher(toml);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String jsonString(String json, String field) {
        var matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return matcher.find() ? matcher.group(1) : null;
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
        public String fileContent(Path path) {
            try {
                return Files.readString(path);
            } catch (IOException e) {
                return null;
            }
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
