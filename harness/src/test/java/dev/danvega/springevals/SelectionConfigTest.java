package dev.danvega.springevals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionConfigTest {

    @TempDir
    Path temp;

    private static final List<String> KNOWN = List.of("claude-sonnet-5", "codex-gpt", "gemini-pro");

    @Test
    void absentFileEnablesEveryAgent() {
        SelectionConfig config = SelectionConfig.load(temp, KNOWN);
        assertFalse(config.restricts());
        assertTrue(config.enabled("claude-sonnet-5"));
        assertTrue(config.enabled("gemini-pro"));
    }

    @Test
    void absentEnabledAgentsKeyEnablesEveryAgent() throws Exception {
        Files.writeString(temp.resolve(SelectionConfig.FILE_NAME), "{\"otherSetting\": true}");
        SelectionConfig config = SelectionConfig.load(temp, KNOWN);
        assertFalse(config.restricts());
        assertTrue(config.enabled("codex-gpt"));
    }

    @Test
    void listedAgentsAreEnabledAndUnlistedAreNot() throws Exception {
        Files.writeString(temp.resolve(SelectionConfig.FILE_NAME),
                "{\"enabledAgents\": [\"claude-sonnet-5\", \"gemini-pro\"]}");
        SelectionConfig config = SelectionConfig.load(temp, KNOWN);
        assertTrue(config.restricts());
        assertTrue(config.enabled("claude-sonnet-5"));
        assertFalse(config.enabled("codex-gpt"));
    }

    @Test
    void unknownAgentNameFailsLoudly() throws Exception {
        Files.writeString(temp.resolve(SelectionConfig.FILE_NAME), "{\"enabledAgents\": [\"claude-sonet-5\"]}");
        var thrown = assertThrows(IllegalArgumentException.class, () -> SelectionConfig.load(temp, KNOWN));
        assertTrue(thrown.getMessage().contains("claude-sonet-5"));
    }

    @Test
    void nonArrayEnabledAgentsFailsLoudly() throws Exception {
        Files.writeString(temp.resolve(SelectionConfig.FILE_NAME), "{\"enabledAgents\": \"claude-sonnet-5\"}");
        assertThrows(IllegalArgumentException.class, () -> SelectionConfig.load(temp, KNOWN));
    }
}
