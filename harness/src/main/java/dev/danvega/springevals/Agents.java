package dev.danvega.springevals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springaicommunity.agents.claude.ClaudeAgentModel;
import org.springaicommunity.agents.claude.ClaudeAgentOptions;
import org.springaicommunity.agents.codex.CodexAgentModel;
import org.springaicommunity.agents.codex.CodexAgentOptions;
import org.springaicommunity.agents.codexsdk.CodexClient;
import org.springaicommunity.agents.gemini.GeminiAgentModel;
import org.springaicommunity.agents.gemini.GeminiAgentOptions;
import org.springaicommunity.agents.geminisdk.GeminiClient;
import org.springaicommunity.agents.model.AgentModel;
import org.springaicommunity.agents.qwencode.QwenCodeAgentModel;
import org.springaicommunity.agents.qwencode.QwenCodeAgentOptions;

/**
 * Loads agent definitions from agents/&lt;name&gt;.json and builds the matching
 * Agent Client model. Adding a provider means adding a case here; adding a
 * model variant is just another JSON file.
 *
 * The optional "env" map is passed to the agent CLI process. Values may
 * reference host environment variables with ${VAR}. This is how open and
 * local models plug in: point a CLI at an OpenAI- or Anthropic-compatible
 * endpoint (Ollama, Docker Model Runner, Moonshot, ...) via env vars.
 */
public class Agents {

    public record AgentSpec(String name, String provider, String model, Map<String, String> env,
            Double budgetUsd, Double estCostPerAttemptUsd) {
    }

    private final Path agentsDir;
    private final ObjectMapper mapper = new ObjectMapper();

    public Agents(Path repoRoot) {
        this.agentsDir = repoRoot.resolve("agents");
    }

    public AgentSpec load(String name) {
        Path file = agentsDir.resolve(name + ".json");
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("agent config not found: " + file);
        }
        try {
            JsonNode node = mapper.readTree(file.toFile());
            Map<String, String> env = new java.util.HashMap<>();
            if (node.has("env")) {
                node.get("env").properties()
                        .forEach(entry -> env.put(entry.getKey(), entry.getValue().asText()));
            }
            if (!node.hasNonNull("name") || !node.hasNonNull("provider") || !node.hasNonNull("model")) {
                throw new IllegalArgumentException("agent config requires name, provider, and model: " + file);
            }
            AgentSpec spec = new AgentSpec(node.get("name").asText(), node.get("provider").asText(),
                    node.get("model").asText(), Map.copyOf(env),
                    node.has("budgetUsd") ? node.get("budgetUsd").asDouble() : null,
                    node.has("estCostPerAttemptUsd") ? node.get("estCostPerAttemptUsd").asDouble() : null);
            validate(file, spec);
            return spec;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void validate(Path file, AgentSpec spec) {
        String fileName = file.getFileName().toString().replace(".json", "");
        if (!fileName.equals(spec.name())) {
            throw new IllegalArgumentException("agent name must match config filename: " + file);
        }
        if (!List.of("claude", "codex", "gemini", "qwen-code").contains(spec.provider())) {
            throw new IllegalArgumentException("unsupported provider in " + file + ": " + spec.provider());
        }
        if (spec.model().isBlank()) {
            throw new IllegalArgumentException("agent model must not be blank: " + file);
        }
        if (spec.budgetUsd() != null && spec.budgetUsd() <= 0) {
            throw new IllegalArgumentException("budgetUsd must be positive: " + file);
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

    /** Expand ${VAR} in all env values. Called at model creation, not load, so estimate works without keys. */
    static Map<String, String> expandAll(Map<String, String> env) {
        Map<String, String> expanded = new java.util.HashMap<>();
        env.forEach((key, value) -> expanded.put(key, expandHostEnv(value)));
        return Map.copyOf(expanded);
    }

    /** Expand ${VAR} references against the host environment. */
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

    public AgentModel createModel(AgentSpec spec, Duration timeout) {
        return switch (spec.provider()) {
            case "claude" -> ClaudeAgentModel.builder()
                    .defaultOptions(ClaudeAgentOptions.builder()
                            .model(spec.model())
                            .yolo(true)
                            .timeout(timeout)
                            .environmentVariables(expandAll(spec.env()))
                            .maxBudgetUsd(spec.budgetUsd())
                            .build())
                    .build();
            case "codex" -> new CodexAgentModel(CodexClient.create(),
                    CodexAgentOptions.builder()
                            .model(spec.model())
                            .fullAuto(true)
                            .timeout(timeout)
                            .build(),
                    null);
            case "gemini" -> {
                try {
                    yield new GeminiAgentModel(GeminiClient.create(),
                            GeminiAgentOptions.builder()
                                    .model(spec.model())
                                    .yolo(true)
                                    .timeout(timeout)
                                    .environmentVariables(expandAll(spec.env()))
                                    .build(),
                            null);
                } catch (Exception e) {
                    throw new IllegalStateException("could not create Gemini CLI client", e);
                }
            }
            case "qwen-code" -> new QwenCodeAgentModel(QwenCodeAgentOptions.builder()
                    .model(spec.model())
                    .yolo(true)
                    .timeout(timeout)
                    .environmentVariables(expandAll(spec.env()))
                    .build());
            default -> throw new IllegalArgumentException(
                    "unknown provider '%s' (supported: claude, codex, gemini, qwen-code)".formatted(spec.provider()));
        };
    }
}
