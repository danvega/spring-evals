package dev.danvega.springevals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Deterministic verdict tier. Order is integrity, then hidden tests, then
 * idiom checks, so a candidate that works but misses the modern mechanism
 * is recorded as functional_only rather than lumped in with failures.
 * JUDGE_COMMAND is the single source of the judged build command.
 */
public class MavenJudge {

    private static final Duration MAVEN_TIMEOUT = Duration.ofMinutes(15);
    private static final Duration RESOLVE_TIMEOUT = Duration.ofMinutes(5);

    /** The one judged build command. Validate and run must not diverge. */
    static final List<String> JUDGE_COMMAND = List.of("./mvnw", "-B", "-ntp",
            "-Dmaven.test.skip=false", "-DskipTests=false", "clean", "test");

    /**
     * Outside target/ so clean cannot touch it; deleted before resolving so a
     * pre-seeded file can never stand in for a real resolution.
     */
    static final String RUNTIME_CLASSPATH_FILE = ".spring-evals-runtime-classpath.txt";

    /** Executes the judged build in a specific environment (the attempt's fresh judge container). */
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
            Pattern.compile("testFailureIgnore", Pattern.CASE_INSENSITIVE),
            // Redirected reports would let pre-seeded surefire XML survive clean.
            Pattern.compile("reportsDirectory", Pattern.CASE_INSENSITIVE),
            // Any dependency-plugin configuration can override the judge's classpath resolution flags.
            Pattern.compile("(?s)maven-dependency-plugin(?:(?!</plugin>).)*?<configuration>", Pattern.CASE_INSENSITIVE));

    /** Direct children of any build element that relocate outputs, so clean cannot be trusted. */
    private static final java.util.Set<String> BUILD_REDIRECTS = java.util.Set.of(
            "directory", "outputDirectory", "testOutputDirectory");

    // A misspelled key in checks.json must fail the judge, never silently drop a check.
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public Judgment judge(EvalDefinition eval, Path workspace, BuildRunner runner) {
        return judge(eval, workspace, true, runner);
    }

    /** Used for the intentionally broken baseline, before mechanism requirements are expected to hold. */
    public Judgment judgeBehaviorOnly(EvalDefinition eval, Path workspace, BuildRunner runner) {
        return judge(eval, workspace, false, runner);
    }

    /**
     * Integrity plus idiom checks without a build; null when both hold. Used by
     * fast tests that prove reference candidates satisfy their own policy.
     */
    Judgment validatePolicy(EvalDefinition eval, Path workspace) {
        return validatePolicy(eval, workspace, null);
    }

    /** Pass a runner when the eval declares requiredRuntimeArtifacts; without one that check refuses to be skipped. */
    Judgment validatePolicy(EvalDefinition eval, Path workspace, BuildRunner runner) {
        SourceChecks checks;
        try {
            checks = loadChecks(eval);
            Judgment integrity = checkIntegrity(eval, workspace, checks);
            if (integrity != null) {
                return integrity;
            }
            if (checks == null) {
                return null;
            }
            IdiomResult idiom = checkIdiom(workspace, checks, runner);
            if (idiom.resolverFailure() != null) {
                return new Judgment(Judgment.Outcome.JUDGE_ERROR, null, null, idiom.resolverFailure(), null);
            }
            return idiom.failure() == null ? null
                    : new Judgment(Judgment.Outcome.FUNCTIONAL_ONLY, null, false, idiom.failure(), null);
        } catch (IOException | JacksonException e) {
            return Judgment.error("could not apply trusted candidate policy", e);
        }
    }

    private Judgment judge(EvalDefinition eval, Path workspace, boolean applyMechanismChecks, BuildRunner runner) {
        Judgment judgment;
        try {
            SourceChecks checks = loadChecks(eval);
            judgment = checkIntegrity(eval, workspace, checks);
            if (judgment == null) {
                judgment = judgeBuild(eval, workspace, applyMechanismChecks ? checks : null, runner);
            }
        } catch (IOException | JacksonException e) {
            judgment = Judgment.error("could not apply trusted candidate policy", e);
        }
        writeLog(workspace, judgment);
        return judgment;
    }

    private Judgment judgeBuild(EvalDefinition eval, Path workspace, SourceChecks checks, BuildRunner runner)
            throws IOException {
        BuildResult result = runner.run(workspace, JUDGE_COMMAND, MAVEN_TIMEOUT);
        String output = result.output() == null ? "" : result.output();
        if (result.timedOut()) {
            return new Judgment(Judgment.Outcome.TEST_FAILURE, false, null,
                    "judged build timed out after " + MAVEN_TIMEOUT.toMinutes() + " minutes", output);
        }
        if (result.exitCode() == 0) {
            String evidence = verifyHiddenTestsExecuted(eval, workspace);
            if (evidence != null) {
                return Judgment.policyFailure(evidence, output);
            }
        }
        boolean testsPassed = result.exitCode() == 0;
        boolean compiled = testsPassed
                || !(output.contains("COMPILATION ERROR") || output.contains("Compilation failure"));
        if (!compiled) {
            // Idiom is judged only once the build reached the test phase.
            return new Judgment(Judgment.Outcome.COMPILE_FAILURE, false, null,
                    "Command failed with exit code " + result.exitCode(), output);
        }
        IdiomResult idiom = checks == null ? IdiomResult.NOT_CHECKED : checkIdiom(workspace, checks, runner);
        if (idiom.resolverFailure() != null) {
            return new Judgment(Judgment.Outcome.JUDGE_ERROR, testsPassed, null, idiom.resolverFailure(), output);
        }
        Boolean idiomatic = checks == null ? null : idiom.failure() == null;
        if (testsPassed) {
            return idiom.failure() == null
                    ? new Judgment(Judgment.Outcome.PASS, true, idiomatic,
                            "hidden tests passed" + (idiomatic == null ? "" : " and idiom checks hold"), output)
                    : new Judgment(Judgment.Outcome.FUNCTIONAL_ONLY, true, false, idiom.failure(), output);
        }
        return new Judgment(Judgment.Outcome.TEST_FAILURE, false, idiomatic,
                "Command failed with exit code " + result.exitCode()
                        + (idiom.failure() == null ? "" : "; " + idiom.failure()), output);
    }

    /** Failure is the first idiom miss; resolverFailure means the judge could not confirm artifacts at all. */
    private record IdiomResult(String failure, String resolverFailure) {
        static final IdiomResult NOT_CHECKED = new IdiomResult(null, null);

        static IdiomResult miss(String failure) {
            return new IdiomResult(failure, null);
        }
    }

    private SourceChecks loadChecks(EvalDefinition eval) throws IOException {
        Path checksFile = eval.evalTestsDir().resolve("checks.json");
        return Files.exists(checksFile) ? mapper.readValue(checksFile.toFile(), SourceChecks.class) : null;
    }

    /** Test suppression and pinned-fixture edits make the build untrustworthy, so nothing else runs. */
    private Judgment checkIntegrity(EvalDefinition eval, Path workspace, SourceChecks checks) throws IOException {
        String pomText = pomPolicyText(readPom(workspace));
        for (Pattern pattern : FORBIDDEN_BUILD_CONFIG) {
            if (pattern.matcher(pomText).find()) {
                return Judgment.policyFailure("candidate build configuration can suppress or redirect hidden tests: "
                        + pattern.pattern());
            }
        }
        String redirect = buildRedirect(readPom(workspace));
        if (redirect != null) {
            return Judgment.policyFailure("candidate build configuration can suppress or redirect hidden tests: "
                    + redirect);
        }
        if (checks == null) {
            return null;
        }
        String pinned = checkPinnedFixtures(eval, workspace, checks.pinned());
        return pinned == null ? null : Judgment.policyFailure(pinned);
    }

    private IdiomResult checkIdiom(Path workspace, SourceChecks checks, BuildRunner runner) throws IOException {
        String sources = readMainSources(workspace);
        String sourceResult = checkPatterns("source", sources, checks.requiredSourcePatterns(),
                checks.forbiddenSourcePatterns());
        if (sourceResult != null) {
            return IdiomResult.miss(sourceResult);
        }
        String pomResult = checkPatterns("pom", pomPolicyText(readPom(workspace)), checks.requiredPomPatterns(),
                checks.forbiddenPomPatterns());
        if (pomResult != null) {
            return IdiomResult.miss(pomResult);
        }
        return checkRuntimeArtifacts(workspace, checks.requiredRuntimeArtifacts(), runner);
    }

    /**
     * Any build element relocating outputs (project or profile level) lets
     * pre-seeded files survive clean, so the judge refuses the pom. Malformed
     * XML falls through; Maven rejects it anyway.
     */
    static String buildRedirect(String xml) {
        try {
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var document = factory.newDocumentBuilder()
                    .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
            var builds = document.getElementsByTagName("build");
            for (int i = 0; i < builds.getLength(); i++) {
                var children = builds.item(i).getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    String name = children.item(j).getLocalName() == null
                            ? children.item(j).getNodeName() : children.item(j).getLocalName();
                    if (children.item(j).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                            && BUILD_REDIRECTS.contains(name)) {
                        return "<build><" + name + ">";
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String readPom(Path workspace) throws IOException {
        Path pom = workspace.resolve("pom.xml");
        return Files.exists(pom) ? Files.readString(pom) : "";
    }

    /**
     * A pinned file must be byte-identical to the eval's project fixture;
     * deletion counts as modified. Workspace bytes only.
     */
    private static String checkPinnedFixtures(EvalDefinition eval, Path workspace, List<String> pinned)
            throws IOException {
        for (String declared : safe(pinned)) {
            Path fixture = eval.projectDir().resolve(declared).normalize();
            Path candidate = workspace.resolve(declared).normalize();
            if (!fixture.startsWith(eval.projectDir().normalize()) || !candidate.startsWith(workspace.normalize())) {
                return "pinned path escapes the workspace: " + declared;
            }
            if (!Files.isRegularFile(fixture)) {
                return "pinned path is not a file in the project fixture: " + declared;
            }
            if (!Files.isRegularFile(candidate) || Files.mismatch(fixture, candidate) >= 0) {
                return "pinned fixture file modified: " + declared;
            }
        }
        return null;
    }

    private static String checkPatterns(String target, String text, List<String> required, List<String> forbidden) {
        for (String expression : safe(required)) {
            if (!Pattern.compile(expression, Pattern.MULTILINE | Pattern.DOTALL).matcher(text).find()) {
                return "required modern Spring mechanism missing from " + target + ": " + expression;
            }
        }
        for (String expression : safe(forbidden)) {
            if (Pattern.compile(expression, Pattern.MULTILINE | Pattern.DOTALL).matcher(text).find()) {
                return "forbidden workaround found in " + target + ": " + expression;
            }
        }
        return null;
    }

    /**
     * Pom regexes cannot see dependency scope, so required artifacts are
     * confirmed on the candidate's resolved runtime classpath instead.
     */
    private static IdiomResult checkRuntimeArtifacts(Path workspace, List<String> coordinates, BuildRunner runner)
            throws IOException {
        if (safe(coordinates).isEmpty()) {
            return IdiomResult.NOT_CHECKED;
        }
        if (runner == null) {
            throw new IllegalStateException("requiredRuntimeArtifacts needs a build runner to resolve the classpath; "
                    + "this check is never skipped");
        }
        Path file = workspace.resolve(RUNTIME_CLASSPATH_FILE);
        Files.deleteIfExists(file);
        BuildResult result = runner.run(workspace, List.of("./mvnw", "-B", "-ntp", "-q",
                "dependency:build-classpath", "-DincludeScope=runtime",
                "-Dmdep.outputFile=" + RUNTIME_CLASSPATH_FILE), RESOLVE_TIMEOUT);
        if (result.exitCode() != 0 || result.timedOut() || !Files.isRegularFile(file)) {
            return new IdiomResult(null, "judge could not resolve the runtime classpath to confirm " + coordinates
                    + (result.timedOut() ? " (timed out)" : result.exitCode() != 0
                            ? " (exit " + result.exitCode() + ")" : " (no classpath file written)"));
        }
        String classpath = Files.readString(file);
        Files.deleteIfExists(file);
        for (String coordinate : coordinates) {
            if (!RuntimeArtifacts.present(classpath, coordinate)) {
                return IdiomResult.miss("required runtime artifact missing from the resolved classpath: " + coordinate);
            }
        }
        return IdiomResult.NOT_CHECKED;
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

    /** Comments are inert to the compiler, so source patterns never see them. */
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
                boolean kotlin = path.getFileName().toString().endsWith(".kt");
                text.append(JavaComments.strip(Files.readString(path), kotlin)).append('\n');
            }
        }
        return text.toString();
    }

    /** Null when every hidden test class ran and recorded no failures. */
    private String verifyHiddenTestsExecuted(EvalDefinition eval, Path workspace) throws IOException {
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
        }
        return missing.isEmpty() ? null
                : "Maven returned success without executing every hidden test: " + missing;
    }

    private record SourceChecks(List<String> requiredSourcePatterns, List<String> forbiddenSourcePatterns,
            List<String> requiredPomPatterns, List<String> forbiddenPomPatterns, List<String> pinned,
            List<String> requiredRuntimeArtifacts) {
    }

    private void writeLog(Path workspace, Judgment judgment) {
        if (judgment.output() != null) {
            try {
                Files.writeString(workspace.resolve("maven-output.log"), judgment.output());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
