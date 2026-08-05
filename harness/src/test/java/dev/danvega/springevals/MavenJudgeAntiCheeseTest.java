package dev.danvega.springevals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exit 0 is never trusted without surefire proof that the hidden tests ran. */
class MavenJudgeAntiCheeseTest {

    private static final String CLEAN_POM = """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>0.0.1-SNAPSHOT</version>
                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-surefire-plugin</artifactId>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """;

    @TempDir
    Path temp;

    @Test
    void rejectsFailureIgnorePropertySpellingBeforeMavenRuns() throws Exception {
        Path workspace = workspaceWithStubMaven();
        Files.writeString(workspace.resolve("pom.xml"), """
                <project><properties>
                    <maven.test.failure.ignore>true</maven.test.failure.ignore>
                </properties></project>
                """);

        var judgment = new MavenJudge().judge(eval(), workspace);

        assertFalse(judgment.pass());
        assertTrue(judgment.reasoning().contains("suppress or redirect hidden tests"));
        assertFalse(Files.exists(workspace.resolve("mvnw-ran.marker")),
                "policy violations must be rejected before any Maven process starts");
    }

    @Test
    void rejectsSurefireTestFailureIgnorePluginConfiguration() throws Exception {
        Path workspace = workspace();
        Files.writeString(workspace.resolve("pom.xml"), """
                <project><build><plugins><plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration><testFailureIgnore>true</testFailureIgnore></configuration>
                </plugin></plugins></build></project>
                """);

        var judgment = new MavenJudge().judge(eval(), workspace);

        assertFalse(judgment.pass());
        assertTrue(judgment.reasoning().contains("suppress or redirect hidden tests"));
    }

    @Test
    void rejectsFailureIgnoreSmuggledThroughArgLine() throws Exception {
        Path workspace = workspace();
        Files.writeString(workspace.resolve("pom.xml"), """
                <project><properties>
                    <argLine>-Dmaven.test.failure.ignore=true</argLine>
                </properties></project>
                """);

        var judgment = new MavenJudge().judge(eval(), workspace);

        assertFalse(judgment.pass());
        assertTrue(judgment.reasoning().contains("suppress or redirect hidden tests"));
    }

    @Test
    void cleanPomPassesTheTrustedCandidatePolicy() throws Exception {
        Path workspace = workspace();
        Files.writeString(workspace.resolve("pom.xml"), CLEAN_POM);

        assertNull(new MavenJudge().validatePolicy(eval(), workspace),
                "an ordinary pom with a plain surefire plugin must not trip the policy");
    }

    @Test
    void missingSurefireReportsDirectoryFailsDespiteBuildSuccess() throws Exception {
        Path workspace = workspaceWithStubMaven();

        var judgment = new MavenJudge().judge(eval(), workspace);

        assertFalse(judgment.pass());
        assertTrue(judgment.reasoning().contains("without executing every hidden test"));
    }

    @Test
    void reportWithZeroExecutedTestsFails() throws Exception {
        Path workspace = workspaceWithStubMaven();
        writeSurefireReport(workspace, "tests=\"0\" failures=\"0\" errors=\"0\"");

        var judgment = new MavenJudge().judge(eval(), workspace);

        assertFalse(judgment.pass());
        assertTrue(judgment.reasoning().contains("zero tests"));
    }

    @Test
    void reportRecordingFailuresFailsDespiteBuildSuccess() throws Exception {
        Path workspace = workspaceWithStubMaven();
        writeSurefireReport(workspace, "tests=\"3\" failures=\"1\" errors=\"0\"");

        var judgment = new MavenJudge().judge(eval(), workspace);

        assertFalse(judgment.pass());
        assertTrue(judgment.reasoning().contains("failing tests despite build success"));
    }

    @Test
    void reportRecordingErrorsFailsDespiteBuildSuccess() throws Exception {
        Path workspace = workspaceWithStubMaven();
        writeSurefireReport(workspace, "tests=\"3\" failures=\"0\" errors=\"2\"");

        var judgment = new MavenJudge().judge(eval(), workspace);

        assertFalse(judgment.pass());
        assertTrue(judgment.reasoning().contains("failing tests despite build success"));
    }

    @Test
    void passingReportWithExecutedTestsPasses() throws Exception {
        Path workspace = workspaceWithStubMaven();
        writeSurefireReport(workspace, "tests=\"3\" failures=\"0\" errors=\"0\"");

        var judgment = new MavenJudge().judge(eval(), workspace);

        assertTrue(judgment.pass());
    }

    /** Eval whose hidden suite is a single test class, com.example.HiddenFlowTest. */
    private EvalDefinition eval() throws IOException {
        Path evalDir = temp.resolve("eval");
        Path hidden = evalDir.resolve("EVAL/src/test/java/com/example");
        Files.createDirectories(hidden);
        Files.writeString(hidden.resolve("HiddenFlowTest.java"), """
                package com.example;
                class HiddenFlowTest { }
                """);
        return new EvalDefinition("boot/000-example", "boot", evalDir, Map.of());
    }

    private Path workspace() throws IOException {
        Path workspace = temp.resolve("workspace");
        Files.createDirectories(workspace);
        return workspace;
    }

    /** Rigged build: exits 0 without running anything, drops a marker proving launch. */
    private Path workspaceWithStubMaven() throws IOException {
        Path workspace = workspace();
        Files.writeString(workspace.resolve("pom.xml"), CLEAN_POM);
        Path mvnw = workspace.resolve("mvnw");
        Files.writeString(mvnw, """
                #!/bin/sh
                touch mvnw-ran.marker
                exit 0
                """);
        assertTrue(mvnw.toFile().setExecutable(true));
        return workspace;
    }

    private void writeSurefireReport(Path workspace, String countAttributes) throws IOException {
        Path reports = workspace.resolve("target/surefire-reports");
        Files.createDirectories(reports);
        Files.writeString(reports.resolve("TEST-com.example.HiddenFlowTest.xml"),
                "<testsuite name=\"com.example.HiddenFlowTest\" " + countAttributes + " skipped=\"0\"/>");
    }
}
