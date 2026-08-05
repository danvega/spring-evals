package dev.danvega.springevals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the workspace isolation behavior: fresh copies never carry agent
 * context files that could steer the model, and hidden-test injection puts the
 * trusted Maven launcher back even when the agent tampered with it.
 */
class WorkspaceStrippingTest {

    @TempDir
    Path repo;

    @Test
    void freshCopyStripsEveryAgentContextFile() throws Exception {
        Path dir = fixture();
        Path project = dir.resolve("project");
        List<String> contextFiles = List.of("CLAUDE.md", "AGENTS.md", "GEMINI.md", "QWEN.md", ".mcp.json",
                ".cursorrules");
        for (String file : contextFiles) {
            Files.writeString(project.resolve(file), "steering instructions");
        }
        Files.createDirectories(project.resolve(".claude"));
        Files.writeString(project.resolve(".claude/settings.json"), "{}");
        Files.createDirectories(project.resolve(".cursor/rules"));
        Files.writeString(project.resolve(".cursor/rules/spring.mdc"), "steering instructions");
        Files.createDirectories(project.resolve(".github/workflows"));
        Files.writeString(project.resolve(".github/copilot-instructions.md"), "steering instructions");
        Files.writeString(project.resolve(".github/workflows/ci.yml"), "name: ci");

        EvalDefinition eval = new EvalDefinition("boot/000-example", "boot", dir, Map.of());
        Path workspace = new Workspaces(repo).freshCopy(eval, "strip-test");
        try {
            for (String file : contextFiles) {
                assertFalse(Files.exists(workspace.resolve(file)), file + " must be stripped");
            }
            assertFalse(Files.exists(workspace.resolve(".claude")), ".claude directory must be stripped");
            assertFalse(Files.exists(workspace.resolve(".cursor")), ".cursor directory must be stripped");
            assertFalse(Files.exists(workspace.resolve(".github/copilot-instructions.md")),
                    "Copilot instructions must be stripped");
            // Project content the eval actually needs survives the strip.
            assertTrue(Files.exists(workspace.resolve("pom.xml")));
            assertTrue(Files.exists(workspace.resolve("src/main/java/App.java")));
            assertTrue(Files.exists(workspace.resolve(".github/workflows/ci.yml")),
                    "unrelated .github content is not collateral damage");
        } finally {
            Workspaces.deleteTree(workspace);
        }
    }

    @Test
    void injectEvalTestsRestoresTrustedLauncherAndDropsAgentTests() throws Exception {
        Path dir = fixture();
        EvalDefinition eval = new EvalDefinition("boot/000-example", "boot", dir, Map.of());
        Workspaces workspaces = new Workspaces(repo);
        Path workspace = workspaces.freshCopy(eval, "launcher-test");
        try {
            // The agent tampers with every launcher surface and writes its own test.
            Files.writeString(workspace.resolve("mvnw"), "#!/bin/sh\nexit 0\n");
            Files.writeString(workspace.resolve("mvnw.cmd"), "exit /b 0");
            Files.writeString(workspace.resolve(".mvn/wrapper/maven-wrapper.properties"), "tampered");
            Files.writeString(workspace.resolve(".mvn/rogue.jar"), "planted");
            Files.createDirectories(workspace.resolve("src/test/java"));
            Files.writeString(workspace.resolve("src/test/java/AgentWrittenTest.java"), "class AgentWrittenTest {}");

            workspaces.injectEvalTests(eval, workspace);

            assertEquals("trusted-launcher", Files.readString(workspace.resolve("mvnw")));
            assertEquals("trusted-launcher-cmd", Files.readString(workspace.resolve("mvnw.cmd")));
            assertEquals("trusted-wrapper", Files.readString(workspace.resolve(".mvn/wrapper/maven-wrapper.properties")));
            assertFalse(Files.exists(workspace.resolve(".mvn/rogue.jar")),
                    "files the agent planted in .mvn must not survive");
            assertTrue(workspace.resolve("mvnw").toFile().canExecute());
            assertFalse(Files.exists(workspace.resolve("src/test/java/AgentWrittenTest.java")),
                    "agent-written tests must be removed before hidden tests run");
            assertTrue(Files.exists(workspace.resolve("src/test/java/com/example/HiddenFlowTest.java")),
                    "hidden eval tests must be injected");
        } finally {
            Workspaces.deleteTree(workspace);
        }
    }

    /** Minimal eval fixture: project with launcher files, plus a hidden EVAL test. */
    private Path fixture() throws IOException {
        Path dir = repo.resolve("evals/boot/000-example");
        Path project = dir.resolve("project");
        Files.createDirectories(project.resolve("src/main/java"));
        Files.createDirectories(project.resolve(".mvn/wrapper"));
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        Files.writeString(project.resolve("src/main/java/App.java"), "class App {}");
        Files.writeString(project.resolve("mvnw"), "trusted-launcher");
        Files.writeString(project.resolve("mvnw.cmd"), "trusted-launcher-cmd");
        Files.writeString(project.resolve(".mvn/wrapper/maven-wrapper.properties"), "trusted-wrapper");
        Path hidden = dir.resolve("EVAL/src/test/java/com/example");
        Files.createDirectories(hidden);
        Files.writeString(hidden.resolve("HiddenFlowTest.java"), "package com.example;\nclass HiddenFlowTest {}\n");
        return dir;
    }
}
