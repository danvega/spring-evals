package dev.danvega.springevals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspacesTest {

    @TempDir
    Path repo;

    @Test
    void candidateWorkspaceIsOutsideRepoAndTrustedLauncherIsRestored() throws Exception {
        Path dir = repo.resolve("evals/boot/000-example");
        Files.createDirectories(dir.resolve("project/.mvn/wrapper"));
        Files.createDirectories(dir.resolve("project/src/main"));
        Files.createDirectories(dir.resolve("EVAL/src/test"));
        Files.writeString(dir.resolve("project/mvnw"), "trusted");
        Files.writeString(dir.resolve("project/.mvn/wrapper/config"), "trusted-config");
        Files.writeString(dir.resolve("project/pom.xml"), "<project/>");
        Files.writeString(dir.resolve("EVAL/src/test/HiddenTest.java"), "class HiddenTest {}");
        EvalDefinition eval = new EvalDefinition("boot/000-example", "boot", dir, Map.of());
        Workspaces workspaces = new Workspaces(repo);
        Path workspace = workspaces.freshCopy(eval, "test");
        try {
            assertFalse(workspace.startsWith(repo));
            Files.writeString(workspace.resolve("mvnw"), "candidate-tampering");

            workspaces.injectEvalTests(eval, workspace);

            assertEquals("trusted", Files.readString(workspace.resolve("mvnw")));
            assertEquals("trusted-config", Files.readString(workspace.resolve(".mvn/wrapper/config")));
            assertTrue(Files.exists(workspace.resolve("src/test/HiddenTest.java")));
        } finally {
            Workspaces.deleteTree(workspace);
        }
    }
}
