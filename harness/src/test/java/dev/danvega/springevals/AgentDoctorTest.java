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
    void reportsAuthenticatedClaudeConfigReadyWithoutGeneration() {
        FakeSystem system = new FakeSystem();
        system.executables.put("claude", true);
        system.commands.put("claude --version", new CommandResult(0, "2.1.221 (Claude Code)", false));
        system.commands.put("claude auth status", new CommandResult(0, "{\"loggedIn\": true}", false));
        AgentSpec spec = new AgentSpec("claude-test", "claude", "model", Map.of(), 2.0, 0.5);

        var report = new AgentDoctor(system).inspect(spec);

        assertEquals(Level.READY, report.level());
        assertTrue(report.findings().stream().anyMatch(f -> f.message().contains("reports logged in")));
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
