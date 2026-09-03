package dev.danvega.springevals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import dev.danvega.springevals.cli.AgentCli;

/**
 * Loads agents/&lt;name&gt;.json. The "env" map (with ${VAR} host references)
 * is the only host state an attempt's container receives.
 */
public class Agents {

    public record AgentSpec(String name, String provider, String model, Map<String, String> env,
            Double estCostPerAttemptUsd) {
    }

    private final Path agentsDir;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public Agents(Path repoRoot) {
        this.agentsDir = repoRoot.resolve("agents");
    }

    public AgentSpec load(String name) {
        Path file = agentsDir.resolve(name + ".json");
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("agent config not found: " + file);
        }
        {
            JsonNode node = mapper.readTree(file.toFile());
            Map<String, String> env = new java.util.HashMap<>();
            if (node.has("env")) {
                node.get("env").properties()
                        .forEach(entry -> env.put(entry.getKey(), entry.getValue().asString()));
            }
            if (!node.hasNonNull("name") || !node.hasNonNull("provider") || !node.hasNonNull("model")) {
                throw new IllegalArgumentException("agent config requires name, provider, and model: " + file);
            }
            AgentSpec spec = new AgentSpec(node.get("name").asString(), node.get("provider").asString(),
                    node.get("model").asString(), Map.copyOf(env),
                    node.has("estCostPerAttemptUsd") ? node.get("estCostPerAttemptUsd").asDouble() : null);
            validate(file, spec);
            return spec;
        }
    }

    private static void validate(Path file, AgentSpec spec) {
        String fileName = file.getFileName().toString().replace(".json", "");
        if (!fileName.equals(spec.name())) {
            throw new IllegalArgumentException("agent name must match config filename: " + file);
        }
        if (!AgentCli.ids().contains(spec.provider())) {
            throw new IllegalArgumentException("unsupported provider in " + file + ": " + spec.provider()
                    + " (supported: " + String.join(", ", AgentCli.ids()) + ")");
        }
        if (spec.model().isBlank()) {
            throw new IllegalArgumentException("agent model must not be blank: " + file);
        }
        if (spec.estCostPerAttemptUsd() != null && spec.estCostPerAttemptUsd() < 0) {
            throw new IllegalArgumentException("estCostPerAttemptUsd must not be negative: " + file);
        }
    }

    public List<AgentSpec> loadAll() {
        return names().stream().map(this::load).toList();
    }

    public List<String> names() {
        try (var files = java.nio.file.Files.list(agentsDir)) {
            return files.filter(f -> f.getFileName().toString().endsWith(".json"))
                    .map(f -> f.getFileName().toString().replace(".json", ""))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Expanded at container start, not load, so estimate works without keys set. */
    public static Map<String, String> expandAll(Map<String, String> env) {
        Map<String, String> expanded = new java.util.HashMap<>();
        env.forEach((key, value) -> expanded.put(key, expandHostEnv(value)));
        return Map.copyOf(expanded);
    }

    static String expandHostEnv(String value) {
        var matcher = java.util.regex.Pattern.compile("\\$\\{([A-Z0-9_]+)}").matcher(value);
        return matcher.replaceAll(match -> {
            String resolved = System.getenv(match.group(1));
            if (resolved == null) {
                throw new IllegalStateException("agent config references unset environment variable: " + match.group(1));
            }
            return java.util.regex.Matcher.quoteReplacement(resolved);
        });
    }
}
