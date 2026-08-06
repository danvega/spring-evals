package dev.danvega.springevals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PinnedFixtureTest {

    @TempDir
    Path temp;

    private static final String PINNED_PATH = "src/main/resources/payment-stub.json";

    @Test
    void modifiedPinnedFileFailsAsPinnedFixtureViolation() throws Exception {
        EvalDefinition eval = evalWithPinned();
        Path workspace = workspaceFrom(eval);
        Files.writeString(workspace.resolve(PINNED_PATH), "{\"tampered\": true}");

        var judgment = new MavenJudge().validatePolicy(eval, workspace);

        assertTrue(judgment != null && !judgment.pass());
        assertTrue(judgment.reasoning().contains("pinned fixture file modified: " + PINNED_PATH));
    }

    @Test
    void deletedPinnedFileFailsAsPinnedFixtureViolation() throws Exception {
        EvalDefinition eval = evalWithPinned();
        Path workspace = workspaceFrom(eval);
        Files.delete(workspace.resolve(PINNED_PATH));

        var judgment = new MavenJudge().validatePolicy(eval, workspace);

        assertTrue(judgment != null && !judgment.pass());
        assertTrue(judgment.reasoning().contains("pinned fixture file modified: " + PINNED_PATH));
    }

    @Test
    void untouchedPinnedFilePasses() throws Exception {
        EvalDefinition eval = evalWithPinned();
        Path workspace = workspaceFrom(eval);

        assertNull(new MavenJudge().validatePolicy(eval, workspace));
    }

    @Test
    void checksWithoutPinnedArrayLeaveModifiedFilesAlone() throws Exception {
        EvalDefinition eval = eval("{\"requiredSourcePatterns\": []}");
        Path workspace = workspaceFrom(eval);
        Files.writeString(workspace.resolve(PINNED_PATH), "{\"tampered\": true}");

        assertNull(new MavenJudge().validatePolicy(eval, workspace));
    }

    @Test
    void pinnedPathOutsideTheWorkspaceIsRejected() throws Exception {
        EvalDefinition eval = eval("{\"pinned\": [\"../escape.txt\"]}");
        Path workspace = workspaceFrom(eval);

        var judgment = new MavenJudge().validatePolicy(eval, workspace);

        assertTrue(judgment != null && !judgment.pass());
        assertTrue(judgment.reasoning().contains("escapes the workspace"));
    }

    @Test
    void pinnedPathAbsentFromTheFixtureIsRejected() throws Exception {
        EvalDefinition eval = eval("{\"pinned\": [\"src/main/resources/never-existed.json\"]}");
        Path workspace = workspaceFrom(eval);

        var judgment = new MavenJudge().validatePolicy(eval, workspace);

        assertTrue(judgment != null && !judgment.pass());
        assertTrue(judgment.reasoning().contains("not a file in the project fixture"));
    }

    private EvalDefinition evalWithPinned() throws Exception {
        return eval("{\"pinned\": [\"" + PINNED_PATH + "\"]}");
    }

    private EvalDefinition eval(String checksJson) throws Exception {
        Path evalDir = temp.resolve("eval-" + Math.abs(checksJson.hashCode()));
        Files.createDirectories(evalDir.resolve("EVAL"));
        Files.writeString(evalDir.resolve("EVAL/checks.json"), checksJson);
        Path fixture = evalDir.resolve("project").resolve(PINNED_PATH);
        Files.createDirectories(fixture.getParent());
        Files.writeString(fixture, "{\"amount\": 100}");
        Files.writeString(evalDir.resolve("project/pom.xml"), "<project/>");
        return new EvalDefinition("boot/000-example", "boot", evalDir, Map.of());
    }

    private Path workspaceFrom(EvalDefinition eval) {
        Path workspace = temp.resolve("ws-" + eval.dir().getFileName());
        Workspaces.copyTree(eval.projectDir(), workspace);
        return workspace;
    }
}
