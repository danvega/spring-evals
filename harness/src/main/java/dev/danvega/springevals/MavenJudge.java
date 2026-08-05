package dev.danvega.springevals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.exec.CommandJudge;
import org.springaicommunity.judge.result.Judgment;

/**
 * The deterministic verdict tier: hidden tests must pass under
 * "./mvnw clean test". Wraps the community CommandJudge, which executes
 * through the agent-sandbox abstraction (LocalSandbox today, DockerSandbox
 * when we flip isolation on).
 */
public class MavenJudge {

    private static final Duration MAVEN_TIMEOUT = Duration.ofMinutes(15);

    private static final List<Pattern> FORBIDDEN_BUILD_CONFIG = List.of(
            Pattern.compile("maven\\.test\\.skip", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<skipTests>\\s*true\\s*</skipTests>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<skip>\\s*true\\s*</skip>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<testSourceDirectory>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<testClassesDirectory>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<(?:excludes|includes)>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("maven\\.test\\.failure\\.ignore", Pattern.CASE_INSENSITIVE),
            Pattern.compile("testFailureIgnore", Pattern.CASE_INSENSITIVE));

    private final CommandJudge delegate = new CommandJudge(
            "./mvnw -B -ntp -Dmaven.test.skip=false -DskipTests=false clean test", 0, MAVEN_TIMEOUT);
    private final ObjectMapper mapper = new ObjectMapper();

    public Judgment judge(EvalDefinition eval, Path workspace) {
        return judge(eval, workspace, true);
    }

    /** Used for the intentionally broken baseline, before mechanism requirements are expected to hold. */
    public Judgment judgeBehaviorOnly(EvalDefinition eval, Path workspace) {
        return judge(eval, workspace, false);
    }

    Judgment validatePolicy(EvalDefinition eval, Path workspace) {
        return validateCandidatePolicy(eval, workspace, true);
    }

    private Judgment judge(EvalDefinition eval, Path workspace, boolean applyMechanismChecks) {
        Judgment policy = validateCandidatePolicy(eval, workspace, applyMechanismChecks);
        if (policy != null) {
            writeLog(workspace, policy);
            return policy;
        }
        Judgment judgment = delegate.judge(JudgmentContext.builder()
                .goal("Hidden eval tests for " + eval.id())
                .workspace(workspace)
                .executionTime(Duration.ZERO)
                .startedAt(Instant.now())
                .build());
        if (judgment.pass()) {
            Judgment reports = verifyHiddenTestsExecuted(eval, workspace);
            if (reports != null) {
                writeLog(workspace, reports);
                return reports;
            }
        }
        writeLog(workspace, judgment);
        return judgment;
    }

    private Judgment validateCandidatePolicy(EvalDefinition eval, Path workspace, boolean applyMechanismChecks) {
        Path pom = workspace.resolve("pom.xml");
        try {
            String pomText = Files.exists(pom) ? Files.readString(pom) : "";
            for (Pattern pattern : FORBIDDEN_BUILD_CONFIG) {
                if (pattern.matcher(pomText).find()) {
                    return Judgment.fail("candidate build configuration can suppress or redirect hidden tests: "
                            + pattern.pattern());
                }
            }

            Path checksFile = eval.evalTestsDir().resolve("checks.json");
            if (!applyMechanismChecks || !Files.exists(checksFile)) {
                return null;
            }
            SourceChecks checks = mapper.readValue(checksFile.toFile(), SourceChecks.class);
            String sources = readMainSources(workspace);
            Judgment sourceResult = checkPatterns("source", sources, checks.requiredSourcePatterns(),
                    checks.forbiddenSourcePatterns());
            if (sourceResult != null) {
                return sourceResult;
            }
            return checkPatterns("pom", pomText, checks.requiredPomPatterns(), checks.forbiddenPomPatterns());
        } catch (IOException e) {
            return Judgment.error("could not apply trusted candidate policy", e);
        }
    }

    private static Judgment checkPatterns(String target, String text, List<String> required, List<String> forbidden) {
        for (String expression : safe(required)) {
            if (!Pattern.compile(expression, Pattern.MULTILINE | Pattern.DOTALL).matcher(text).find()) {
                return Judgment.fail("required modern Spring mechanism missing from " + target + ": " + expression);
            }
        }
        for (String expression : safe(forbidden)) {
            if (Pattern.compile(expression, Pattern.MULTILINE | Pattern.DOTALL).matcher(text).find()) {
                return Judgment.fail("forbidden workaround found in " + target + ": " + expression);
            }
        }
        return null;
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String readMainSources(Path workspace) throws IOException {
        Path main = workspace.resolve("src/main");
        if (!Files.isDirectory(main)) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        try (Stream<Path> paths = Files.walk(main)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java")
                            || path.getFileName().toString().endsWith(".kt"))
                    .sorted().toList()) {
                text.append(Files.readString(path)).append('\n');
            }
        }
        return text.toString();
    }

    private Judgment verifyHiddenTestsExecuted(EvalDefinition eval, Path workspace) {
        List<String> missing = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(eval.evalTestsDir())) {
            for (Path test : paths.filter(p -> p.getFileName().toString().endsWith("Test.java")).toList()) {
                String source = Files.readString(test);
                var packageMatcher = Pattern.compile("(?m)^package\\s+([a-zA-Z0-9_.]+)\\s*;").matcher(source);
                String className = test.getFileName().toString().replace(".java", "");
                String qualified = packageMatcher.find() ? packageMatcher.group(1) + "." + className : className;
                Path report = workspace.resolve("target/surefire-reports/TEST-" + qualified + ".xml");
                if (!Files.exists(report)) {
                    missing.add(qualified);
                    continue;
                }
                String xml = Files.readString(report);
                var tests = Pattern.compile("tests=\"(\\d+)\"").matcher(xml);
                if (!tests.find() || Integer.parseInt(tests.group(1)) == 0) {
                    missing.add(qualified + " (zero tests)");
                    continue;
                }
                var failures = Pattern.compile("(?:failures|errors)=\"(\\d+)\"").matcher(xml);
                while (failures.find()) {
                    if (Integer.parseInt(failures.group(1)) > 0) {
                        missing.add(qualified + " (report records failing tests despite build success)");
                        break;
                    }
                }
            }
        } catch (IOException e) {
            return Judgment.error("could not verify hidden-test execution reports", e);
        }
        return missing.isEmpty() ? null
                : Judgment.fail("Maven returned success without executing every hidden test: " + missing);
    }

    private record SourceChecks(List<String> requiredSourcePatterns, List<String> forbiddenSourcePatterns,
            List<String> requiredPomPatterns, List<String> forbiddenPomPatterns) {
    }

    private void writeLog(Path workspace, Judgment judgment) {
        Object output = judgment.metadata() != null ? judgment.metadata().get("output") : null;
        if (output != null) {
            try {
                Files.writeString(workspace.resolve("maven-output.log"), output.toString());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
