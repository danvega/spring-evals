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
import dev.danvega.springevals.cli.AgentCli;
import dev.danvega.springevals.cli.Finding;
import dev.danvega.springevals.cli.Finding.Level;
import dev.danvega.springevals.cli.HostProbe;

/**
 * Zero-generation readiness checks. Everything is judged from what reaches the
 * container: the expanded agent config env and the CLI's seeded files. Host
 * logins and host-installed CLIs never count.
 */
final class AgentDoctor {

    record Report(AgentSpec spec, List<Finding> findings) {
        Level level() {
            return findings.stream().map(Finding::level).max(Enum::compareTo).orElse(Level.READY);
        }
    }

    private static final Pattern ENV_REFERENCE = Pattern.compile("\\$\\{([A-Z0-9_]+)}");

    private final HostProbe host;

    AgentDoctor() {
        this(new RealHost());
    }

    AgentDoctor(HostProbe host) {
        this.host = host;
    }

    /** Excluded agents are still fully inspected; exclusion is selection, not readiness. */
    int print(List<AgentSpec> specs, java.util.Set<String> excluded) {
        System.out.println("Agent configuration doctor (no model prompts or generation requests)\n");
        List<Report> reports = specs.stream().map(this::inspect).toList();
        for (Report report : reports) {
            System.out.printf("%-9s %s  (%s / %s)%s%n", report.level(), report.spec().name(),
                    report.spec().provider(), report.spec().model(),
                    excluded.contains(report.spec().name())
                            ? "  [excluded by " + SelectionConfig.FILE_NAME + ": skipped by --all-agents and --family]"
                            : "");
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
        System.out.println("Every attempt runs in a fresh container that sees only the agent config env "
                + "and the files its CLI seeds; host logins, host context files, and host-installed CLIs never reach it.");
        return blocked == 0 ? 0 : 1;
    }

    Report inspect(AgentSpec spec) {
        List<Finding> findings = new ArrayList<>();
        AgentCli cli;
        try {
            cli = AgentCli.forProvider(spec.provider());
        } catch (IllegalArgumentException e) {
            findings.add(Finding.blocked(e.getMessage()));
            return new Report(spec, List.copyOf(findings));
        }
        findings.add(Finding.ready("CLI in the benchmark image: " + cli.npmPackage() + "@" + cli.pinnedVersion()));

        Map<String, String> containerEnv = resolveEnvironment(spec, findings);
        if (spec.estCostPerAttemptUsd() == null) {
            findings.add(Finding.blocked(
                    "estCostPerAttemptUsd is missing; paid-run cost protection will refuse this config"));
        } else {
            findings.add(Finding.ready(
                    "configured estimate: $%.2f per attempt".formatted(spec.estCostPerAttemptUsd())));
        }

        boolean missingReferences = findings.stream().anyMatch(f -> f.level() == Level.BLOCKED
                && f.message().startsWith("missing environment variable"));
        if (missingReferences) {
            return new Report(spec, List.copyOf(findings));
        }
        boolean customEndpoint = containerEnv.containsKey("ANTHROPIC_BASE_URL")
                || containerEnv.containsKey("OPENAI_BASE_URL");
        if (customEndpoint) {
            String credential = containerEnv.getOrDefault("ANTHROPIC_API_KEY", containerEnv.get("OPENAI_API_KEY"));
            findings.add(credential == null || credential.isBlank()
                    ? Finding.blocked("custom endpoint has no configured API credential")
                    : Finding.ready("custom endpoint credential is configured"));
        } else {
            findings.addAll(cli.doctor(spec, containerEnv, host));
        }
        checkEndpointReachability(containerEnv, findings);
        return new Report(spec, List.copyOf(findings));
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
                String hostValue = host.environment(variable);
                if (hostValue == null || hostValue.isBlank()) {
                    findings.add(Finding.blocked("missing environment variable required by config: " + variable));
                    missing = true;
                    hostValue = "";
                } else {
                    findings.add(Finding.ready("required environment variable is set: " + variable));
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

    /**
     * Inside the container, localhost is the container. A host-side model server
     * must be addressed as host.docker.internal, which the sandbox maps to the host.
     */
    private void checkEndpointReachability(Map<String, String> env, List<Finding> findings) {
        String baseUrl = env.getOrDefault("OPENAI_BASE_URL", env.get("ANTHROPIC_BASE_URL"));
        if (baseUrl == null) {
            return;
        }
        String hostName = hostOf(baseUrl);
        if (hostName == null) {
            return;
        }
        if (isLoopback(hostName)) {
            findings.add(Finding.blocked("the endpoint points at " + hostName + ", which inside the sandbox "
                    + "container is the container itself; use host.docker.internal instead"));
            return;
        }
        if (!hostName.equalsIgnoreCase("host.docker.internal")) {
            return;
        }
        String model = env.getOrDefault("OPENAI_MODEL", "");
        HostProbe.ProbeResult probe = host.probeLocalModels(baseUrl.replaceFirst("(?i)host\\.docker\\.internal",
                "localhost"), env.get("OPENAI_API_KEY"), model);
        if (!probe.reachable()) {
            findings.add(Finding.blocked(probe.message()));
        } else if (!model.isBlank() && !probe.modelPresent()) {
            findings.add(Finding.blocked(probe.message()));
        } else {
            findings.add(Finding.ready(probe.message()));
        }
    }

    private static String hostOf(String value) {
        try {
            return URI.create(value).getHost();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isLoopback(String hostName) {
        return "localhost".equalsIgnoreCase(hostName) || "127.0.0.1".equals(hostName) || "::1".equals(hostName)
                || "[::1]".equals(hostName);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static final class RealHost implements HostProbe {

        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

        @Override
        public String environment(String name) {
            return System.getenv(name);
        }

        @Override
        public Path home() {
            return Path.of(System.getProperty("user.home"));
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
                return new ProbeResult(false, false, "local endpoint is not reachable from the host: " + baseUrl);
            }
        }
    }
}
