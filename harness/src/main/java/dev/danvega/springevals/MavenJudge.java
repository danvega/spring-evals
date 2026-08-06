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
 * Deterministic verdict tier; host and docker mode must run the exact same
 * Maven command, and JUDGE_COMMAND is the single source of it.
 */
public class MavenJudge {

    private static final Duration MAVEN_TIMEOUT = Duration.ofMinutes(15);

    /** The one judged build command. Host and docker mode must not diverge. */
    static final List<String> JUDGE_COMMAND = List.of("./mvnw", "-B", "-ntp",
            "-Dmaven.test.skip=false", "-DskipTests=false", "clean", "test");

    /** Executes the judged build in a specific environment (e.g. the attempt's container). */
    interface BuildRunner {
        BuildResult run(Path workspace, List<String> command, Duration timeout);
    }

    record BuildResult(int exitCode, String output, boolean timedOut) {
    }

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
            String.join(" ", JUDGE_COMMAND), 0, MAVEN_TIMEOUT);
    private final ObjectMapper mapper = new ObjectMapper();

    public Judgment judge(EvalDefinition eval, Path workspace) {
        return judge(eval, workspace, true, null);
    }

    /** Used for the intentionally broken baseline, before mechanism requirements are expected to hold. */
    public Judgment judgeBehaviorOnly(EvalDefinition eval, Path workspace) {
        return judge(eval, workspace, false, null);
    }

    public Judgment judge(EvalDefinition eval, Path workspace, BuildRunner runner) {
        return judge(eval, workspace, true, runner);
    }

    public Judgment judgeBehaviorOnly(EvalDefinition eval, Path workspace, BuildRunner runner) {
        return judge(eval, workspace, false, runner);
    }

    Judgment validatePolicy(EvalDefinition eval, Path workspace) {
        return validateCandidatePolicy(eval, workspace, true);
    }

    private Judgment judge(EvalDefinition eval, Path workspace, boolean applyMechanismChecks, BuildRunner runner) {
        Judgment policy = validateCandidatePolicy(eval, workspace, applyMechanismChecks);
        if (policy != null) {
            writeLog(workspace, policy);
            return policy;
        }
        Judgment judgment = runner == null ? judgeOnHost(eval, workspace) : judgeWithRunner(workspace, runner);
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

    private Judgment judgeOnHost(EvalDefinition eval, Path workspace) {
        return delegate.judge(JudgmentContext.builder()
                .goal("Hidden eval tests for " + eval.id())
                .workspace(workspace)
                .executionTime(Duration.ZERO)
                .startedAt(Instant.now())
                .build());
    }

    private static Judgment judgeWithRunner(Path workspace, BuildRunner runner) {
        BuildResult result = runner.run(workspace, JUDGE_COMMAND, MAVEN_TIMEOUT);
        if (result.timedOut()) {
            return Judgment.builder()
                    .status(org.springaicommunity.judge.result.JudgmentStatus.FAIL)
                    .reasoning("judged build timed out after " + MAVEN_TIMEOUT.toMinutes() + " minutes")
                    .metadata("output", result.output() == null ? "" : result.output())
                    .build();
        }
        return Judgment.builder()
                .status(result.exitCode() == 0
                        ? org.springaicommunity.judge.result.JudgmentStatus.PASS
                        : org.springaicommunity.judge.result.JudgmentStatus.FAIL)
                .reasoning(result.exitCode() == 0
                        ? "Command completed successfully with exit code 0"
                        : "Command failed with exit code " + result.exitCode())
                .metadata("output", result.output() == null ? "" : result.output())
                .build();
    }

    private Judgment validateCandidatePolicy(EvalDefinition eval, Path workspace, boolean applyMechanismChecks) {
        Path pom = workspace.resolve("pom.xml");
        try {
            // Comments are inert to Maven, so pom patterns must never see them.
            String pomText = pomPolicyText(Files.exists(pom) ? Files.readString(pom) : "");
            for (Pattern pattern : FORBIDDEN_BUILD_CONFIG) {
                if (pattern.matcher(pomText).find()) {
                    return Judgment.fail("candidate build configuration can suppress or redirect hidden tests: "
                            + pattern.pattern());
                }
            }

            Path checksFile = eval.evalTestsDir().resolve("checks.json");
            if (!Files.exists(checksFile)) {
                return null;
            }
            SourceChecks checks = mapper.readValue(checksFile.toFile(), SourceChecks.class);
            Judgment pinned = checkPinnedFixtures(eval, workspace, checks.pinned());
            if (pinned != null) {
                return pinned;
            }
            if (!applyMechanismChecks) {
                return null;
            }
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

    /**
     * A pinned file must be byte-identical to the eval's project fixture;
     * deletion counts as modified. Workspace bytes only, so both sandbox
     * modes enforce it identically.
     */
    private static Judgment checkPinnedFixtures(EvalDefinition eval, Path workspace, List<String> pinned)
            throws IOException {
        for (String declared : safe(pinned)) {
            Path fixture = eval.projectDir().resolve(declared).normalize();
            Path candidate = workspace.resolve(declared).normalize();
            if (!fixture.startsWith(eval.projectDir().normalize()) || !candidate.startsWith(workspace.normalize())) {
                return Judgment.fail("pinned path escapes the workspace: " + declared);
            }
            if (!Files.isRegularFile(fixture)) {
                return Judgment.fail("pinned path is not a file in the project fixture: " + declared);
            }
            if (!Files.isRegularFile(candidate) || Files.mismatch(fixture, candidate) >= 0) {
                return Judgment.fail("pinned fixture file modified: " + declared);
            }
        }
        return null;
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

    /**
     * XML-parsed (comments dropped as nodes, CDATA coalesced) so CDATA-spliced
     * fake comment markers cannot hide active configuration from the patterns.
     */
    static String pomPolicyText(String xml) {
        try {
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setIgnoringComments(true);
            factory.setCoalescing(true);
            var document = factory.newDocumentBuilder()
                    .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
            var transformerFactory = javax.xml.transform.TransformerFactory.newInstance();
            transformerFactory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            var transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            var writer = new java.io.StringWriter();
            transformer.transform(new javax.xml.transform.dom.DOMSource(document),
                    new javax.xml.transform.stream.StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            return stripXmlComments(xml);
        }
    }

    /** Fallback for malformed XML only; such a pom fails the Maven build anyway. */
    static String stripXmlComments(String xml) {
        return xml.replaceAll("(?s)<!--.*?(?:-->|\\z)", "");
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
            List<String> requiredPomPatterns, List<String> forbiddenPomPatterns, List<String> pinned) {
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
