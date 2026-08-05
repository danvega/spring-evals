package dev.danvega.springevals;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerSandboxTest {

    @Test
    void buildsHeadlessAgentCommandsPerProvider() {
        assertEquals(List.of("claude", "-p", "fix it", "--model", "claude-x",
                "--dangerously-skip-permissions", "--output-format", "json"),
                DockerSandbox.agentCommand("claude", "claude-x", "fix it"));
        // Codex's own sandbox would leave the bind-mounted workspace read-only.
        assertEquals(List.of("codex", "exec", "--skip-git-repo-check",
                "--dangerously-bypass-approvals-and-sandbox", "-m", "gpt-x", "fix it"),
                DockerSandbox.agentCommand("codex", "gpt-x", "fix it"));
        assertEquals(List.of("gemini", "-m", "gemini-x", "-y", "-p", "fix it"),
                DockerSandbox.agentCommand("gemini", "gemini-x", "fix it"));
        assertEquals(List.of("qwen", "-y", "-m", "grok-x", "-p", "fix it"),
                DockerSandbox.agentCommand("qwen-code", "grok-x", "fix it"));
        assertThrows(IllegalArgumentException.class,
                () -> DockerSandbox.agentCommand("cursor", "m", "p"));
    }

    @Test
    void parsesClaudeHeadlessJsonAndToleratesOtherOutput() {
        var parsed = DockerSandbox.parseClaudeJson("""
                npm warn something
                {"type":"result","subtype":"success","is_error":false,"result":"Fixed the bean.",
                 "total_cost_usd":0.42,"usage":{"input_tokens":1200,"output_tokens":345}}
                """);
        assertEquals(0.42, parsed.costUsd());
        assertEquals(1200L, parsed.inputTokens());
        assertEquals(345L, parsed.outputTokens());
        assertEquals("Fixed the bean.", parsed.resultText());

        assertEquals(null, DockerSandbox.parseClaudeJson("Not logged in · Please run /login"));
        assertEquals(null, DockerSandbox.parseClaudeJson(null));
        assertEquals(null, DockerSandbox.parseClaudeJson("{\"unrelated\":true}"));
    }

    @Test
    void imageTagIsContentAddressedFromTheDockerfile() {
        java.nio.file.Path root = java.nio.file.Path.of("").toAbsolutePath().getParent();
        String tag = DockerSandbox.imageTag(root);
        assertTrue(tag.startsWith(DockerSandbox.IMAGE_NAME + ":"));
        assertEquals(DockerSandbox.IMAGE_NAME.length() + 1 + 12, tag.length());
    }

    @Test
    void mapsProvidersToCliBinaries() {
        assertEquals("claude", DockerSandbox.cliBinary("claude"));
        assertEquals("codex", DockerSandbox.cliBinary("codex"));
        assertEquals("gemini", DockerSandbox.cliBinary("gemini"));
        assertEquals("qwen", DockerSandbox.cliBinary("qwen-code"));
    }

    @Test
    void prunesOnlyContainersWhoseOwnerProcessIsDead() {
        assertTrue(DockerSandbox.ownerAlive(String.valueOf(ProcessHandle.current().pid())));
        assertTrue(!DockerSandbox.ownerAlive(""));
        assertTrue(!DockerSandbox.ownerAlive("not-a-pid"));
        assertTrue(!DockerSandbox.ownerAlive("999999999"));
    }

    @Test
    void rejectsUnknownSandboxMode() {
        assertThrows(IllegalArgumentException.class, () -> DockerSandbox.resolveMode("podman"));
        assertEquals("host", DockerSandbox.resolveMode("host"));
    }

    @Test
    void judgeCommandIsTheSingleSharedDefinition() {
        assertEquals("./mvnw -B -ntp -Dmaven.test.skip=false -DskipTests=false clean test",
                String.join(" ", MavenJudge.JUDGE_COMMAND));
    }
}
