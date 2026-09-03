package dev.danvega.springevals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.danvega.springevals.Agents.AgentSpec;
import dev.danvega.springevals.cli.AgentCli;
import dev.danvega.springevals.cli.ClaudeCli;
import dev.danvega.springevals.cli.CodexCli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The registry and the image must agree: a CLI the harness drives is exactly the one the Dockerfile installs. */
class AgentCliTest {

    @Test
    void serviceLoaderDiscoversEveryShippedCli() {
        assertEquals(List.of("claude", "codex", "gemini", "qwen-code"), AgentCli.ids());
        assertEquals("qwen", AgentCli.forProvider("qwen-code").binary());
        var thrown = assertThrows(IllegalArgumentException.class, () -> AgentCli.forProvider("cursor"));
        assertTrue(thrown.getMessage().contains("claude, codex, gemini, qwen-code"));
    }

    @Test
    void dockerfileInstallsEveryPinnedVersion() throws Exception {
        // Tests run with harness/ as the working directory.
        String dockerfile = Files.readString(Path.of("docker/Dockerfile"));
        for (AgentCli cli : AgentCli.all()) {
            String pin = cli.npmPackage() + "@" + cli.pinnedVersion();
            assertTrue(dockerfile.contains(pin), "Dockerfile must install " + pin);
        }
    }

    @Test
    void buildsHeadlessCommandsPerCli() {
        assertEquals(List.of("claude", "-p", "fix it", "--model", "claude-x",
                "--dangerously-skip-permissions", "--output-format", "stream-json", "--verbose"),
                AgentCli.forProvider("claude").headlessCommand("fix it", "claude-x"));
        // Codex's own sandbox would leave the bind-mounted workspace read-only.
        assertEquals(List.of("codex", "exec", "--skip-git-repo-check",
                "--dangerously-bypass-approvals-and-sandbox", "--json", "-m", "gpt-x", "fix it"),
                AgentCli.forProvider("codex").headlessCommand("fix it", "gpt-x"));
        assertEquals(List.of("gemini", "-m", "gemini-x", "-y", "--skip-trust", "-o", "stream-json", "-p", "fix it"),
                AgentCli.forProvider("gemini").headlessCommand("fix it", "gemini-x"));
        assertEquals(List.of("qwen", "-y", "-m", "grok-x", "-o", "stream-json", "-p", "fix it"),
                AgentCli.forProvider("qwen-code").headlessCommand("fix it", "grok-x"));
    }

    @Test
    void everyLaneIsItsOwnProvider() {
        for (AgentCli cli : AgentCli.all()) {
            assertEquals(cli.id(), cli.lane());
        }
    }

    @Test
    void claudeParsesHeadlessJsonAndToleratesOtherOutput() {
        var parsed = new ClaudeCli().parse("""
                npm warn something
                {"type":"result","subtype":"success","is_error":false,"result":"Fixed the bean.",
                 "total_cost_usd":0.42,"usage":{"input_tokens":1200,"output_tokens":345}}
                """, 0);
        assertEquals(0.42, parsed.costUsd());
        assertEquals(1200L, parsed.inputTokens());
        assertEquals(345L, parsed.outputTokens());
        assertEquals("Fixed the bean.", parsed.responseText());

        var plain = new ClaudeCli().parse("Not logged in · Please run /login", 1);
        assertEquals("Not logged in · Please run /login", plain.responseText());
        assertNull(plain.costUsd());
        assertNull(new ClaudeCli().parse(null, 1).responseText());
        assertNull(new ClaudeCli().parse("{\"unrelated\":true}", 0).costUsd());
    }

    @Test
    void onlyCodexSeedsAHostFileAndOnlyItsCredential() {
        Path home = Path.of("/fake-home");
        AgentSpec spec = new AgentSpec("codex-test", "codex", "model", Map.of(), 0.1);
        var seeds = new CodexCli().seedFiles(spec, home);
        assertEquals(1, seeds.size());
        assertEquals(home.resolve(".codex/auth.json"), seeds.getFirst().hostPath());
        assertEquals("/sandbox/codex-home/auth.json", seeds.getFirst().containerPath());
        for (AgentCli cli : AgentCli.all()) {
            if (!cli.id().equals("codex")) {
                assertTrue(cli.seedFiles(spec, home).isEmpty(), cli.id() + " must seed nothing from the host");
            }
        }
    }
}
