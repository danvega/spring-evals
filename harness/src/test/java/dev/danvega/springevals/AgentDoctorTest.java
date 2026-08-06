package dev.danvega.springevals;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.danvega.springevals.AgentDoctor.CommandResult;
import dev.danvega.springevals.AgentDoctor.Level;
import dev.danvega.springevals.AgentDoctor.ProbeResult;
import dev.danvega.springevals.Agents.AgentSpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDoctorTest {

    @Test
    void claudeIsReadyWithApiKeyForIsolatedRuns() {
        FakeSystem system = new FakeSystem();
        system.executables.put("claude", true);
        system.environment.put("ANTHROPIC_API_KEY", "sk-test");
        system.commands.put("claude --version", new CommandResult(0, "2.1.221 (Claude Code)", false));
        AgentSpec spec = new AgentSpec("claude-test", "claude", "model", Map.of(), 2.0, 0.5);

        var report = new AgentDoctor(system).inspect(spec);

        assertEquals(Level.READY, report.level());
        assertTrue(report.findings().stream().anyMatch(f -> f.message().contains("isolated Claude config dir")));
    }

    @Test
    void claudeIsBlockedWithoutAnyCredentialBecauseIsolationDisablesLogin() {
        FakeSystem system = new FakeSystem();
        system.executables.put("claude", true);
        system.commands.put("claude --version", new CommandResult(0, "2.1.221 (Claude Code)", false));
        AgentSpec spec = new AgentSpec("claude-test", "claude", "model", Map.of(), 2.0, 0.5);

        var report = new AgentDoctor(system).inspect(spec);

        assertEquals(Level.BLOCKED, report.level());
        assertTrue(report.findings().stream()
                .anyMatch(f -> f.message().contains("CLAUDE_CODE_OAUTH_TOKEN")
                        && f.message().contains("ANTHROPIC_API_KEY")));
    }

    @Test
    void claudeIsReadyWithSubscriptionTokenFromSetupToken() {
        FakeSystem system = new FakeSystem();
        system.executables.put("claude", true);
        system.environment.put("CLAUDE_CODE_OAUTH_TOKEN", "sk-ant-oat-test");
        system.commands.put("claude --version", new CommandResult(0, "2.1.221 (Claude Code)", false));
        AgentSpec spec = new AgentSpec("claude-test", "claude", "model", Map.of(), 2.0, 0.5);

        var report = new AgentDoctor(system).inspect(spec);

        assertEquals(Level.READY, report.level());
        assertTrue(report.findings().stream()
                .anyMatch(f -> f.message().contains("subscription token")));
    }

    @Test
    void claudeWarnsWhenConfigDeclaresBothSubscriptionTokenAndApiKey() {
        FakeSystem system = new FakeSystem();
        system.executables.put("claude", true);
        system.commands.put("claude --version", new CommandResult(0, "2.1.221 (Claude Code)", false));
        AgentSpec spec = new AgentSpec("claude-test", "claude", "model", Map.of(
                "CLAUDE_CODE_OAUTH_TOKEN", "sk-ant-oat-test",
                "ANTHROPIC_API_KEY", "sk-test"), 2.0, 0.5);

        var report = new AgentDoctor(system).inspect(spec);

        assertEquals(Level.WARNING, report.level());
        assertTrue(report.findings().stream()
                .anyMatch(f -> f.message().contains("prefers the API key")));
    }

    @Test
    void claudeDoesNotWarnForHostApiKeyBecauseTheRunStripsIt() {
        FakeSystem system = new FakeSystem();
        system.executables.put("claude", true);
        system.environment.put("ANTHROPIC_API_KEY", "sk-host");
        system.commands.put("claude --version", new CommandResult(0, "2.1.221 (Claude Code)", false));
        AgentSpec spec = new AgentSpec("claude-test", "claude", "model",
                Map.of("CLAUDE_CODE_OAUTH_TOKEN", "sk-ant-oat-test"), 2.0, 0.5);

        var report = new AgentDoctor(system).inspect(spec);

        assertEquals(Level.READY, report.level());
    }


    @Test
    void reportsCodexSubscriptionBillingFromChatGptSignIn() {
        FakeSystem system = new FakeSystem();
        system.executables.put("codex", true);
        system.commands.put("codex --version", new CommandResult(0, "0.1.0", false));
        String authPath = java.nio.file.Path.of(System.getProperty("user.home"), ".codex", "auth.json").toString();
        system.files.put(authPath, true);
        system.fileContents.put(authPath,
                "{\"OPENAI_API_KEY\": null, \"auth_mode\": \"chatgpt\", \"tokens\": {\"access_token\": \"x\"}}");
        AgentSpec spec = new AgentSpec("codex-test", "codex", "model", Map.of(), null, 0.5);

        var report = new AgentDoctor(system).inspect(spec);

        assertTrue(report.findings().stream()
                .anyMatch(f -> f.message().contains("billing: ChatGPT sign-in")));
    }

    @Test
    void warnsWhenCodexHasBothChatGptLoginAndEnvKey() {
        FakeSystem system = new FakeSystem();
        system.executables.put("codex", true);
        system.environment.put("OPENAI_API_KEY", "sk-test");
        system.commands.put("codex --version", new CommandResult(0, "0.1.0", false));
        String authPath = java.nio.file.Path.of(System.getProperty("user.home"), ".codex", "auth.json").toString();
        system.files.put(authPath, true);
        system.fileContents.put(authPath,
                "{\"OPENAI_API_KEY\": null, \"auth_mode\": \"chatgpt\", \"tokens\": {\"access_token\": \"x\"}}");
        AgentSpec spec = new AgentSpec("codex-test", "codex", "model", Map.of(), null, 0.5);

        var report = new AgentDoctor(system).inspect(spec);

        assertTrue(report.findings().stream()
                .anyMatch(f -> f.message().contains("preferred_auth_method")));
    }

    @Test
    void blocksMissingReferencedSecretWithoutExposingAValue() {
        FakeSystem system = new FakeSystem();
        system.executables.put("claude", true);
        system.commands.put("claude --version", new CommandResult(0, "version", false));
        AgentSpec spec = new AgentSpec("kimi", "claude", "model", Map.of(
                "ANTHROPIC_BASE_URL", "https://example.test/anthropic",
                "ANTHROPIC_API_KEY", "${MISSING_KEY}"), 1.0, 0.3);

        var report = new AgentDoctor(system).inspect(spec);

        assertEquals(Level.BLOCKED, report.level());
        assertTrue(report.findings().stream().anyMatch(f -> f.message().contains("MISSING_KEY")));
    }

    @Test
    void verifiesLocalEndpointAndModelWithoutGeneration() {
        FakeSystem system = new FakeSystem();
        system.executables.put("qwen", true);
        system.commands.put("qwen --version", new CommandResult(0, "qwen 1", false));
        system.probe = new ProbeResult(true, true, "local endpoint is reachable and model is available: local-model");
        AgentSpec spec = new AgentSpec("local", "qwen-code", "local-model", Map.of(
                "OPENAI_BASE_URL", "http://localhost:11434/v1",
                "OPENAI_API_KEY", "local-placeholder",
                "OPENAI_MODEL", "local-model"), null, 0.0);

        var report = new AgentDoctor(system).inspect(spec);

        assertEquals(Level.READY, report.level());
        assertTrue(system.probed);
    }

    private static final class FakeSystem implements AgentDoctor.SystemAccess {
        final Map<String, String> environment = new HashMap<>();
        final Map<String, Boolean> files = new HashMap<>();
        final Map<String, String> fileContents = new HashMap<>();
        final Map<String, Boolean> executables = new HashMap<>();
        final Map<String, CommandResult> commands = new HashMap<>();
        ProbeResult probe = new ProbeResult(false, false, "unreachable");
        boolean probed;

        @Override
        public String environment(String name) {
            return environment.get(name);
        }

        @Override
        public boolean fileExists(Path path) {
            return files.getOrDefault(path.toString(), false);
        }

        @Override
        public String fileContent(Path path) {
            return fileContents.get(path.toString());
        }

        @Override
        public boolean executable(String command) {
            return executables.getOrDefault(command, false);
        }

        @Override
        public CommandResult command(List<String> command) {
            return commands.getOrDefault(String.join(" ", command), new CommandResult(1, "", false));
        }

        @Override
        public ProbeResult probeLocalModels(String baseUrl, String apiKey, String model) {
            probed = true;
            return probe;
        }
    }
}
