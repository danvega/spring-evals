package dev.danvega.springevals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChecksFileTest {

    @TempDir
    Path temp;

    @Test
    void unknownKeyInChecksFileIsAJudgeError() throws Exception {
        EvalDefinition eval = eval("{\"requiredPomPattern\": [\"spring-boot-starter-webmvc\"]}");
        Path workspace = workspaceFrom(eval);

        var judgment = new MavenJudge().validatePolicy(eval, workspace);

        assertTrue(judgment != null && judgment.isError(),
                "a misspelled check key must fail the judge instead of silently dropping the check");
        assertTrue(judgment.reasoning().contains("could not apply trusted candidate policy"));
    }

    @Test
    void knownKeysOnlyPass() throws Exception {
        EvalDefinition eval = eval("{\"requiredPomPatterns\": [], \"forbiddenSourcePatterns\": []}");
        Path workspace = workspaceFrom(eval);

        assertNull(new MavenJudge().validatePolicy(eval, workspace));
    }

    private EvalDefinition eval(String checksJson) throws Exception {
        Path evalDir = temp.resolve("eval-" + Math.abs(checksJson.hashCode()));
        Files.createDirectories(evalDir.resolve("EVAL"));
        Files.writeString(evalDir.resolve("EVAL/checks.json"), checksJson);
        Files.createDirectories(evalDir.resolve("project/src/main/java"));
        Files.writeString(evalDir.resolve("project/pom.xml"), "<project/>");
        return new EvalDefinition("boot/000-example", "boot", evalDir, Map.of());
    }

    private Path workspaceFrom(EvalDefinition eval) {
        Path workspace = temp.resolve("ws-" + eval.dir().getFileName());
        Workspaces.copyTree(eval.projectDir(), workspace);
        return workspace;
    }
}
