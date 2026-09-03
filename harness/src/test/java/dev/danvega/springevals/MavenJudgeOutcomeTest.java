package dev.danvega.springevals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hidden tests always run after integrity holds; the idiom checks decide pass versus functional_only. */
class MavenJudgeOutcomeTest {

    @TempDir
    Path temp;

    /** Runner that fakes the judged build and records every command it was asked to run. */
    private static final class ScriptedRunner implements MavenJudge.BuildRunner {
        final List<List<String>> commands = new ArrayList<>();
        private final int exitCode;
        private final String output;
        private final boolean writeReport;
        private final String classpath;

        ScriptedRunner(int exitCode, String output, boolean writeReport, String classpath) {
            this.exitCode = exitCode;
            this.output = output;
            this.writeReport = writeReport;
            this.classpath = classpath;
        }

        @Override
        public MavenJudge.BuildResult run(Path workspace, List<String> command, Duration timeout) {
            commands.add(command);
            try {
                if (command.contains("dependency:build-classpath")) {
                    Path file = workspace.resolve(MavenJudge.RUNTIME_CLASSPATH_FILE);
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, classpath);
                    return new MavenJudge.BuildResult(0, "", false);
                }
                if (writeReport) {
                    Path reports = workspace.resolve("target/surefire-reports");
                    Files.createDirectories(reports);
                    Files.writeString(reports.resolve("TEST-com.example.HiddenFlowTest.xml"),
                            "<testsuite tests=\"2\" failures=\"0\" errors=\"0\"/>");
                }
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            return new MavenJudge.BuildResult(exitCode, output, false);
        }
    }

    @Test
    void passingTestsWithIdiomHeldIsPass() throws Exception {
        EvalDefinition eval = eval("{\"requiredSourcePatterns\": [\"JsonMapper\"]}");
        Path workspace = workspace("class A { JsonMapper m; }");
        ScriptedRunner runner = new ScriptedRunner(0, "BUILD SUCCESS", true, "");

        Judgment judgment = new MavenJudge().judge(eval, workspace, runner);

        assertEquals(Judgment.Outcome.PASS, judgment.outcome());
        assertEquals(true, judgment.testsPassed());
        assertEquals(true, judgment.idiomatic());
        assertNull(judgment.failureKind());
    }

    @Test
    void passingTestsWithIdiomMissedIsFunctionalOnlyAndStillRanTheTests() throws Exception {
        EvalDefinition eval = eval("{\"requiredSourcePatterns\": [\"JsonMapper\"]}");
        Path workspace = workspace("class A { ObjectMapper m; }");
        ScriptedRunner runner = new ScriptedRunner(0, "BUILD SUCCESS", true, "");

        Judgment judgment = new MavenJudge().judge(eval, workspace, runner);

        assertEquals(Judgment.Outcome.FUNCTIONAL_ONLY, judgment.outcome());
        assertEquals(true, judgment.testsPassed());
        assertEquals(false, judgment.idiomatic());
        assertEquals("idiom_failure", judgment.failureKind());
        assertTrue(judgment.reasoning().contains("required modern Spring mechanism missing from source"));
        assertEquals(MavenJudge.JUDGE_COMMAND, runner.commands.getFirst(), "the build must run before idiom checks");
    }

    @Test
    void idiomMentionedOnlyInACommentStillHolds() throws Exception {
        EvalDefinition eval = eval("{\"forbiddenSourcePatterns\": [\"new\\\\s+ObjectMapper\"]}");
        Path workspace = workspace("/** replaces the old new ObjectMapper() bean */ class A { JsonMapper m; }");
        ScriptedRunner runner = new ScriptedRunner(0, "BUILD SUCCESS", true, "");

        assertEquals(Judgment.Outcome.PASS, new MavenJudge().judge(eval, workspace, runner).outcome());
    }

    @Test
    void failingTestsRecordTheIdiomResultButStayAFailure() throws Exception {
        EvalDefinition eval = eval("{\"requiredSourcePatterns\": [\"JsonMapper\"]}");
        Path workspace = workspace("class A { ObjectMapper m; }");
        ScriptedRunner runner = new ScriptedRunner(1, "[ERROR] Tests run: 2, Failures: 1", false, "");

        Judgment judgment = new MavenJudge().judge(eval, workspace, runner);

        assertEquals(Judgment.Outcome.TEST_FAILURE, judgment.outcome());
        assertEquals(false, judgment.testsPassed());
        assertEquals(false, judgment.idiomatic());
        assertEquals("test_failure", judgment.failureKind());
    }

    @Test
    void compilationErrorsAreClassifiedFromTheBuildOutput() throws Exception {
        EvalDefinition eval = eval(null);
        Path workspace = workspace("class A {");
        ScriptedRunner runner = new ScriptedRunner(1, "[ERROR] COMPILATION ERROR : cannot find symbol", false, "");

        Judgment judgment = new MavenJudge().judge(eval, workspace, runner);

        assertEquals(Judgment.Outcome.COMPILE_FAILURE, judgment.outcome());
        assertNull(judgment.idiomatic(), "an eval without idiom checks records no idiom verdict");
    }

    @Test
    void noChecksFileRecordsNoIdiomVerdict() throws Exception {
        EvalDefinition eval = eval(null);
        Path workspace = workspace("class A { }");
        ScriptedRunner runner = new ScriptedRunner(0, "BUILD SUCCESS", true, "");

        Judgment judgment = new MavenJudge().judge(eval, workspace, runner);

        assertEquals(Judgment.Outcome.PASS, judgment.outcome());
        assertNull(judgment.idiomatic());
    }

    @Test
    void behaviorOnlyJudgingSkipsIdiomChecks() throws Exception {
        EvalDefinition eval = eval("{\"requiredSourcePatterns\": [\"JsonMapper\"]}");
        Path workspace = workspace("class A { ObjectMapper m; }");
        ScriptedRunner runner = new ScriptedRunner(0, "BUILD SUCCESS", true, "");

        Judgment judgment = new MavenJudge().judgeBehaviorOnly(eval, workspace, runner);

        assertEquals(Judgment.Outcome.PASS, judgment.outcome());
        assertNull(judgment.idiomatic());
    }

    @Test
    void pinnedFixtureEditStopsBeforeTheBuild() throws Exception {
        EvalDefinition eval = eval("{\"pinned\": [\"src/main/java/Stub.java\"]}");
        Path fixture = eval.projectDir().resolve("src/main/java/Stub.java");
        Files.createDirectories(fixture.getParent());
        Files.writeString(fixture, "class Stub {}");
        Path workspace = workspace("class A { }");
        Files.writeString(workspace.resolve("src/main/java/Stub.java"), "class Stub { int edited; }");
        ScriptedRunner runner = new ScriptedRunner(0, "BUILD SUCCESS", true, "");

        Judgment judgment = new MavenJudge().judge(eval, workspace, runner);

        assertEquals(Judgment.Outcome.POLICY_FAILURE, judgment.outcome());
        assertNull(judgment.testsPassed());
        assertTrue(runner.commands.isEmpty(), "integrity violations must never start the build");
    }

    @Test
    void successWithoutHiddenTestEvidenceIsAPolicyFailure() throws Exception {
        EvalDefinition eval = eval("{}");
        Path workspace = workspace("class A { }");
        ScriptedRunner runner = new ScriptedRunner(0, "BUILD SUCCESS", false, "");

        Judgment judgment = new MavenJudge().judge(eval, workspace, runner);

        assertEquals(Judgment.Outcome.POLICY_FAILURE, judgment.outcome());
        assertEquals(false, judgment.testsPassed());
        assertTrue(judgment.reasoning().contains("without executing every hidden test"));
    }

    @Test
    void requiredRuntimeArtifactsAreResolvedFromTheClasspathInTheJudgeContainer() throws Exception {
        EvalDefinition eval = eval("{\"requiredRuntimeArtifacts\": [\"org.springframework.boot:spring-boot-h2console\"]}");
        Path workspace = workspace("class A { }");
        String present = "/r/org/springframework/boot/spring-boot-h2console/4.1.1/spring-boot-h2console-4.1.1.jar";
        ScriptedRunner resolved = new ScriptedRunner(0, "BUILD SUCCESS", true, present);
        ScriptedRunner missing = new ScriptedRunner(0, "BUILD SUCCESS", true, "/r/com/h2database/h2/2.4.240/h2-2.4.240.jar");

        assertEquals(Judgment.Outcome.PASS, new MavenJudge().judge(eval, workspace, resolved).outcome());
        assertTrue(resolved.commands.stream().anyMatch(c -> c.contains("dependency:build-classpath")));
        Judgment judgment = new MavenJudge().judge(eval, workspace, missing);
        assertEquals(Judgment.Outcome.FUNCTIONAL_ONLY, judgment.outcome());
        assertTrue(judgment.reasoning().contains("required runtime artifact missing"));
    }

    @Test
    void resolverFailureIsAJudgeErrorNeverAnIdiomMiss() throws Exception {
        EvalDefinition eval = eval("{\"requiredRuntimeArtifacts\": [\"com.h2database:h2\"]}");
        Path workspace = workspace("class A { }");
        MavenJudge.BuildRunner failing = (ws, command, timeout) -> {
            if (command.contains("dependency:build-classpath")) {
                return new MavenJudge.BuildResult(1, "resolution failed", false);
            }
            return new ScriptedRunner(0, "BUILD SUCCESS", true, "").run(ws, command, timeout);
        };
        MavenJudge.BuildRunner silent = (ws, command, timeout) -> {
            if (command.contains("dependency:build-classpath")) {
                return new MavenJudge.BuildResult(0, "", false);
            }
            return new ScriptedRunner(0, "BUILD SUCCESS", true, "").run(ws, command, timeout);
        };

        Judgment nonzero = new MavenJudge().judge(eval, workspace, failing);
        Judgment noFile = new MavenJudge().judge(eval, workspace, silent);

        assertEquals(Judgment.Outcome.JUDGE_ERROR, nonzero.outcome());
        assertEquals("judge_error", nonzero.failureKind());
        assertEquals(true, nonzero.testsPassed(), "the test result is still recorded");
        assertEquals(Judgment.Outcome.JUDGE_ERROR, noFile.outcome());
        assertTrue(noFile.reasoning().contains("no classpath file written"));
    }

    @Test
    void preSeededClasspathFileIsDeletedBeforeResolving() throws Exception {
        EvalDefinition eval = eval("{\"requiredRuntimeArtifacts\": [\"com.h2database:h2\"]}");
        Path workspace = workspace("class A { }");
        Files.writeString(workspace.resolve(MavenJudge.RUNTIME_CLASSPATH_FILE),
                "/r/com/h2database/h2/2.4.240/h2-2.4.240.jar");
        MavenJudge.BuildRunner silent = (ws, command, timeout) -> {
            if (command.contains("dependency:build-classpath")) {
                return new MavenJudge.BuildResult(0, "", false);
            }
            return new ScriptedRunner(0, "BUILD SUCCESS", true, "").run(ws, command, timeout);
        };

        assertEquals(Judgment.Outcome.JUDGE_ERROR, new MavenJudge().judge(eval, workspace, silent).outcome());
    }

    @Test
    void runtimeArtifactsWithoutARunnerRefuseToBeSkipped() throws Exception {
        EvalDefinition eval = eval("{\"requiredRuntimeArtifacts\": [\"com.h2database:h2\"]}");
        Path workspace = workspace("class A { }");

        assertThrows(IllegalStateException.class, () -> new MavenJudge().validatePolicy(eval, workspace));
    }

    @Test
    void compileFailureRecordsNoIdiomAndResolvesNothing() throws Exception {
        EvalDefinition eval = eval("{\"requiredSourcePatterns\": [\"JsonMapper\"], \"requiredRuntimeArtifacts\": [\"com.h2database:h2\"]}");
        Path workspace = workspace("class A { JsonMapper m; }");
        ScriptedRunner runner = new ScriptedRunner(1, "[ERROR] COMPILATION ERROR : cannot find symbol", false, "");

        Judgment judgment = new MavenJudge().judge(eval, workspace, runner);

        assertEquals(Judgment.Outcome.COMPILE_FAILURE, judgment.outcome());
        assertNull(judgment.idiomatic());
        assertTrue(runner.commands.stream().noneMatch(c -> c.contains("dependency:build-classpath")));
    }

    @Test
    void buildTimeoutRecordsNoIdiom() throws Exception {
        EvalDefinition eval = eval("{\"requiredSourcePatterns\": [\"JsonMapper\"]}");
        Path workspace = workspace("class A { ObjectMapper m; }");
        MavenJudge.BuildRunner timingOut = (ws, command, timeout) -> new MavenJudge.BuildResult(-1, "", true);

        Judgment judgment = new MavenJudge().judge(eval, workspace, timingOut);

        assertEquals(Judgment.Outcome.TEST_FAILURE, judgment.outcome());
        assertNull(judgment.idiomatic());
    }

    @Test
    void buildRedirectsAndDependencyPluginConfigurationArePolicyFailures() throws Exception {
        EvalDefinition eval = eval("{}");
        for (String pom : List.of(
                "<project><build><directory>elsewhere</directory></build></project>",
                "<project><profiles><profile><build><outputDirectory>x</outputDirectory></build></profile></profiles></project>",
                "<project><build><plugins><plugin><artifactId>maven-surefire-plugin</artifactId>"
                        + "<configuration><reportsDirectory>keep</reportsDirectory></configuration></plugin></plugins></build></project>",
                "<project><build><plugins><plugin><artifactId>maven-dependency-plugin</artifactId>"
                        + "<configuration><includeScope>test</includeScope></configuration></plugin></plugins></build></project>")) {
            Path workspace = workspace("class A { }");
            Files.writeString(workspace.resolve("pom.xml"), pom);
            ScriptedRunner runner = new ScriptedRunner(0, "BUILD SUCCESS", true, "");

            Judgment judgment = new MavenJudge().judge(eval, workspace, runner);

            assertEquals(Judgment.Outcome.POLICY_FAILURE, judgment.outcome(), pom);
            assertTrue(runner.commands.isEmpty(), "the build must not start: " + pom);
        }
        Path legit = workspace("class A { }");
        Files.writeString(legit.resolve("pom.xml"), "<project><build><resources><resource>"
                + "<directory>src/main/resources</directory></resource></resources></build></project>");
        assertEquals(Judgment.Outcome.PASS,
                new MavenJudge().judge(eval, legit, new ScriptedRunner(0, "BUILD SUCCESS", true, "")).outcome(),
                "a resource directory is not a build redirect");
    }

    @Test
    void validatePolicyReportsIdiomMissesWithoutABuild() throws Exception {
        EvalDefinition eval = eval("{\"forbiddenPomPatterns\": [\"autoconfigure-classic\"]}");
        Path workspace = workspace("class A { }");
        Files.writeString(workspace.resolve("pom.xml"), "<project><artifactId>spring-boot-autoconfigure-classic</artifactId></project>");

        Judgment judgment = new MavenJudge().validatePolicy(eval, workspace);

        assertEquals(Judgment.Outcome.FUNCTIONAL_ONLY, judgment.outcome());
        assertFalse(judgment.pass());
    }

    private EvalDefinition eval(String checksJson) throws IOException {
        Path evalDir = temp.resolve("eval-" + Math.abs(String.valueOf(checksJson).hashCode()));
        Path hidden = evalDir.resolve("EVAL/src/test/java/com/example");
        Files.createDirectories(hidden);
        Files.writeString(hidden.resolve("HiddenFlowTest.java"), "package com.example;\nclass HiddenFlowTest { }\n");
        Files.createDirectories(evalDir.resolve("project"));
        Files.writeString(evalDir.resolve("project/pom.xml"), "<project/>");
        if (checksJson != null) {
            Files.writeString(evalDir.resolve("EVAL/checks.json"), checksJson);
        }
        return new EvalDefinition("boot/000-example", "boot", evalDir, Map.of());
    }

    private Path workspace(String mainSource) throws IOException {
        Path workspace = temp.resolve("ws-" + Math.abs(mainSource.hashCode()) + "-" + System.nanoTime());
        Files.createDirectories(workspace.resolve("src/main/java"));
        Files.writeString(workspace.resolve("src/main/java/A.java"), mainSource);
        Files.writeString(workspace.resolve("pom.xml"), "<project/>");
        return workspace;
    }
}
