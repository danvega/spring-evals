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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the doctor's host-context contamination warnings: global instruction
 * files and MCP configuration for the non-Claude CLIs must surface as
 * warnings, and the content-aware checks trigger only on the exact markers
 * the implementation looks for. Everything runs through the SystemAccess
 * fake; the real home directory is never read.
 */
class AgentDoctorContaminationTest {

    private static final Path HOME = Path.of(System.getProperty("user.home"));

    @Test
    void codexWarnsWhenGlobalAgentsFileExists() {
        FakeSystem system = codexSystem();
        system.files.put(HOME.resolve(".codex/AGENTS.md").toString(), true);

        var report = new AgentDoctor(system).inspect(codexSpec());

        assertEquals(Level.WARNING, report.level());
        assertTrue(hasFinding(report, "~/.codex/AGENTS.md exists"));
    }

    @Test
    void codexWarnsWhenConfigTomlDefinesMcpServers() {
        FakeSystem system = codexSystem();
        system.fileContents.put(HOME.resolve(".codex/config.toml").toString(),
                "[mcp_servers.github]\ncommand = \"github-mcp\"\n");

        var report = new AgentDoctor(system).inspect(codexSpec());

        assertTrue(hasFinding(report, "~/.codex/config.toml defines MCP servers or instructions"));
    }

    @Test
    void codexConfigTomlWithoutMcpOrInstructionsIsNotFlagged() {
        FakeSystem system = codexSystem();
        system.fileContents.put(HOME.resolve(".codex/config.toml").toString(), "model = \"some-model\"\n");

        var report = new AgentDoctor(system).inspect(codexSpec());

        assertFalse(hasFinding(report, "~/.codex/config.toml defines"));
    }

    @Test
    void geminiWarnsWhenGlobalGeminiMdExists() {
        FakeSystem system = geminiSystem();
        system.files.put(HOME.resolve(".gemini/GEMINI.md").toString(), true);

        var report = new AgentDoctor(system).inspect(geminiSpec());

        assertEquals(Level.WARNING, report.level());
        assertTrue(hasFinding(report, "~/.gemini/GEMINI.md exists"));
    }

    @Test
    void geminiWarnsWhenSettingsDefineMcpServers() {
        FakeSystem system = geminiSystem();
        system.fileContents.put(HOME.resolve(".gemini/settings.json").toString(),
                "{\"mcpServers\": {\"context7\": {}}}");

        var report = new AgentDoctor(system).inspect(geminiSpec());

        assertEquals(Level.WARNING, report.level());
        assertTrue(hasFinding(report, "~/.gemini/settings.json defines MCP servers"));
    }

    @Test
    void geminiSettingsCheckIsContentAwareNotFilenameBased() {
        // The implementation looks for the quoted JSON key, so prose that
        // merely mentions mcpServers must not trip the warning.
        FakeSystem system = geminiSystem();
        system.fileContents.put(HOME.resolve(".gemini/settings.json").toString(),
                "{\"note\": \"no mcp servers here; mcpServers stays unset\"}");

        var report = new AgentDoctor(system).inspect(geminiSpec());

        assertFalse(hasFinding(report, "~/.gemini/settings.json defines"));
    }

    @Test
    void qwenWarnsForGlobalQwenMdAndMcpSettings() {
        FakeSystem system = new FakeSystem();
        system.executables.put("qwen", true);
        system.commands.put("qwen --version", new CommandResult(0, "qwen 1", false));
        system.files.put(HOME.resolve(".qwen/QWEN.md").toString(), true);
        system.fileContents.put(HOME.resolve(".qwen/settings.json").toString(),
                "{\"mcpServers\": {\"context7\": {}}}");
        // Remote OPENAI-compatible endpoint keeps authentication READY so the
        // report level isolates the contamination warnings.
        AgentSpec spec = new AgentSpec("qwen-test", "qwen-code", "model", Map.of(
                "OPENAI_BASE_URL", "https://example.test/v1",
                "OPENAI_API_KEY", "test-key"), 0.5, 0.1, true);

        var report = new AgentDoctor(system).inspect(spec);

        assertEquals(Level.WARNING, report.level());
        assertTrue(hasFinding(report, "~/.qwen/QWEN.md exists"));
        assertTrue(hasFinding(report, "~/.qwen/settings.json defines MCP servers"));
    }

    @Test
    void cleanHostProducesNoContaminationWarnings() {
        var report = new AgentDoctor(geminiSystem()).inspect(geminiSpec());

        assertEquals(Level.READY, report.level());
        assertFalse(hasFinding(report, "move it aside"));
        assertFalse(hasFinding(report, "MCP servers"));
    }

    private static boolean hasFinding(AgentDoctor.Report report, String fragment) {
        return report.findings().stream().anyMatch(f -> f.message().contains(fragment));
    }

    /** Codex host with a ChatGPT sign-in so authentication is not the blocker. */
    private static FakeSystem codexSystem() {
        FakeSystem system = new FakeSystem();
        system.executables.put("codex", true);
        system.commands.put("codex --version", new CommandResult(0, "codex 0.1.0", false));
        String authPath = HOME.resolve(".codex/auth.json").toString();
        system.files.put(authPath, true);
        system.fileContents.put(authPath,
                "{\"OPENAI_API_KEY\": null, \"tokens\": {\"access_token\": \"x\"}}");
        return system;
    }

    private static AgentSpec codexSpec() {
        return new AgentSpec("codex-test", "codex", "model", Map.of(), 0.5, 0.1, true);
    }

    private static FakeSystem geminiSystem() {
        FakeSystem system = new FakeSystem();
        system.executables.put("gemini", true);
        system.commands.put("gemini --version", new CommandResult(0, "gemini 1.0", false));
        system.environment.put("GEMINI_API_KEY", "test-key");
        return system;
    }

    private static AgentSpec geminiSpec() {
        return new AgentSpec("gemini-test", "gemini", "model", Map.of(), 0.5, 0.1, true);
    }

    /** Same shape as AgentDoctorTest's fake: everything answered from maps. */
    private static final class FakeSystem implements AgentDoctor.SystemAccess {
        final Map<String, String> environment = new HashMap<>();
        final Map<String, Boolean> files = new HashMap<>();
        final Map<String, String> fileContents = new HashMap<>();
        final Map<String, Boolean> executables = new HashMap<>();
        final Map<String, CommandResult> commands = new HashMap<>();

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
            throw new AssertionError("no local endpoint probe expected in these tests");
        }
    }
}
