package dev.danvega.springevals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenJudgeTest {

    @TempDir
    Path temp;

    @Test
    void rejectsCandidateConfigurationThatSkipsTestsBeforeStartingMaven() throws Exception {
        Path evalDir = temp.resolve("eval");
        Path workspace = temp.resolve("workspace");
        Files.createDirectories(evalDir.resolve("EVAL"));
        Files.createDirectories(workspace);
        Files.writeString(workspace.resolve("pom.xml"), """
                <project><properties><skipTests>true</skipTests></properties></project>
                """);
        EvalDefinition eval = new EvalDefinition("boot/000-example", "boot", evalDir, Map.of());

        var judgment = new MavenJudge().judge(eval, workspace);

        assertFalse(judgment.pass());
        assertTrue(judgment.reasoning().contains("suppress or redirect hidden tests"));
    }
}
