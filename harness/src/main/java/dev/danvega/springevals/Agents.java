package dev.danvega.springevals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * Loads agents/&lt;name&gt;.json and builds the matching Agent Client model;
 * the "env" map (with ${VAR} host references) is how local endpoints plug in.
 */
public class Agents {

    public record AgentSpec(String name, String provider, String model, Map<String, String> env,
            Double budgetUsd, Double estCostPerAttemptUsd) {
    }

    /**
     * Process-env changes around one attempt (removals, then overrides); the
     * SDK options env is a dead store in agent-claude 0.16.0, this is what runs.
     */
    public record EnvPlan(Map<String, String> overrides, Set<String> removals) {
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

    /** Expanded at model creation, not load, so estimate works without keys set. */
    static Map<String, String> expandAll(Map<String, String> env) {
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

    static String sterileClaudeConfigDir() {
        try {
            return Files.createTempDirectory("spring-evals-claude-config-").toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Host-mode contamination barrier; never weaken. Claude auth must come from
     * the agent config env (host login never reaches the sterile config dir).
     */
    public EnvPlan envPlan(AgentSpec spec) {
        Map<String, String> overrides = new java.util.HashMap<>(expandAll(spec.env()));
        // SERVER_PORT=0: agent-started apps must never collide with host apps on 8080.
        overrides.putIfAbsent("SERVER_PORT", "0");
        Set<String> removals = new java.util.HashSet<>();
        // Agent-spawned builds must not inherit the JVM flags the wrapper exports for EnvSandbox.
        removals.add("MAVEN_OPTS");
        switch (spec.provider()) {
            // Prefix-based: ANTHROPIC*/CLAUDE* host vars can silently redirect
            // billing or the endpoint; config-declared values survive via overrides.
            case "claude" -> System.getenv().keySet().stream()
                    .filter(key -> key.startsWith("ANTHROPIC") || key.startsWith("CLAUDE"))
                    .forEach(removals::add);
            // GOOGLE_CLOUD_PROJECT stays (OAuth setups need it); credential material is stripped.
            case "gemini" -> removals.addAll(Set.of("GOOGLE_API_KEY", "GEMINI_API_KEY",
                    "GOOGLE_APPLICATION_CREDENTIALS", "GOOGLE_GENAI_USE_VERTEXAI"));
            default -> {
            }
        }
        removals.removeAll(overrides.keySet());
        if ("claude".equals(spec.provider())) {
            overrides.put("CLAUDE_CONFIG_DIR", sterileClaudeConfigDir());
        }
        return new EnvPlan(Map.copyOf(overrides), Set.copyOf(removals));
    }

    /**
     * Plan overrides must already be applied via EnvSandbox when the model runs;
     * the SDK environmentVariables below are a dead store in 0.16.0.
     */
    public AgentModel createModel(AgentSpec spec, Duration timeout, EnvPlan plan) {
        return switch (spec.provider()) {
            case "claude" -> ClaudeAgentModel.builder()
                    .defaultOptions(ClaudeAgentOptions.builder()
                            .model(spec.model())
                            .yolo(true)
                            .timeout(timeout)
                            .environmentVariables(plan.overrides())
                            .maxBudgetUsd(spec.budgetUsd())
                            .settingSources(List.of())
                            .build())
                    .build();
            // skipGitCheck: workspaces are not git repos and Codex refuses them without it.
            case "codex" -> new CodexAgentModel(CodexClient.create(),
                    CodexAgentOptions.builder()
                            .model(spec.model())
                            .sandboxMode(org.springaicommunity.agents.codexsdk.types.SandboxMode.WORKSPACE_WRITE)
                            .skipGitCheck(true)
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
                                    .environmentVariables(plan.overrides())
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
                    .environmentVariables(plan.overrides())
                    .build());
            default -> throw new IllegalArgumentException(
                    "unknown provider '%s' (supported: claude, codex, gemini, qwen-code)".formatted(spec.provider()));
        };
    }
}
