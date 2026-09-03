package dev.danvega.springevals;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every link an agent makes inside the container is absolute to /workspace and
 * dangles on the host, so the gate must run before anything walks the tree,
 * and the tree helpers must never follow a link out of the workspace.
 */
class WorkspaceGateTest {

    @TempDir
    Path temp;

    @Test
    void danglingAbsoluteLinkAtSrcIsRefusedBeforeHashingOrInjection() throws Exception {
        Path ws = Files.createDirectories(temp.resolve("ws"));
        Files.writeString(ws.resolve("pom.xml"), "<project/>");
        Files.createSymbolicLink(ws.resolve("src"), Path.of("/workspace/real-src"));

        Judgment gate = Main.workspaceGate(ws);
        assertNotNull(gate);
        assertEquals(Judgment.Outcome.POLICY_FAILURE, gate.outcome());
        assertTrue(gate.reasoning().contains("symbolic link in workspace: src"), gate.reasoning());

        // Injection after a refusal must still be safe for validate and any later caller.
        EvalDefinition eval = eval();
        new Workspaces(temp).injectEvalTests(eval, ws);
        assertTrue(Files.isDirectory(ws.resolve("src"), LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.isRegularFile(ws.resolve("src/test/java/HiddenTest.java")));
    }

    @Test
    void resolvingLinkOutOfTheWorkspaceIsDeletedAsALinkOnly() throws Exception {
        Path outside = Files.createDirectories(temp.resolve("outside/test"));
        Files.writeString(outside.resolve("keep.txt"), "host data");
        Path ws = Files.createDirectories(temp.resolve("ws"));
        Files.writeString(ws.resolve("pom.xml"), "<project/>");
        Files.createSymbolicLink(ws.resolve("src"), temp.resolve("outside"));

        Judgment gate = Main.workspaceGate(ws);
        assertNotNull(gate);
        assertEquals(Judgment.Outcome.POLICY_FAILURE, gate.outcome());

        new Workspaces(temp).injectEvalTests(eval(), ws);
        assertTrue(Files.isRegularFile(outside.resolve("keep.txt")), "nothing outside the workspace may be deleted");
        assertFalse(Files.exists(outside.resolve("java")), "nothing may be written outside the workspace");
        assertTrue(Files.isDirectory(ws.resolve("src"), LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.isRegularFile(ws.resolve("src/test/java/HiddenTest.java")));
    }

    @Test
    void unreadableDirectoryIsAJudgeErrorNotALostSample() throws Exception {
        Path ws = Files.createDirectories(temp.resolve("ws"));
        Files.writeString(ws.resolve("pom.xml"), "<project/>");
        Path locked = Files.createDirectories(ws.resolve("src/main/locked"));
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
        try {
            Judgment gate = Main.workspaceGate(ws);
            if (gate == null) {
                return; // running as a user that can read everything; the walk cannot be made to fail
            }
            assertEquals(Judgment.Outcome.JUDGE_ERROR, gate.outcome());
            assertTrue(gate.reasoning().contains("walked"), gate.reasoning());
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    void cleanWorkspacePassesTheGate() throws Exception {
        Path ws = Files.createDirectories(temp.resolve("ws"));
        Files.writeString(ws.resolve("pom.xml"), "<project/>");
        Files.createDirectories(ws.resolve("src/main/java"));
        assertNull(Main.workspaceGate(ws));
    }

    private EvalDefinition eval() throws Exception {
        Path dir = Files.createDirectories(temp.resolve("eval"));
        Files.createDirectories(dir.resolve("project"));
        Files.writeString(dir.resolve("project/mvnw"), "#!/bin/sh\n");
        Path tests = Files.createDirectories(dir.resolve("EVAL/src/test/java"));
        Files.writeString(tests.resolve("HiddenTest.java"), "class HiddenTest {}");
        return new EvalDefinition("boot/000-example", "boot", dir, Map.of());
    }
}
