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

    @Test
    void commentedOutArtifactIdDoesNotSatisfyARequiredPomPattern() throws Exception {
        EvalDefinition eval = evalRequiringPomPattern("<artifactId>spring-boot-starter-webmvc</artifactId>");
        Path workspace = workspaceWithPom("""
                <project>
                  <dependencies>
                    <!--
                    <dependency>
                      <artifactId>spring-boot-starter-webmvc</artifactId>
                    </dependency>
                    -->
                  </dependencies>
                </project>
                """);

        var judgment = new MavenJudge().validatePolicy(eval, workspace);

        assertTrue(judgment != null && !judgment.pass());
        assertTrue(judgment.reasoning().contains("required modern Spring mechanism missing from pom"));
    }

    @Test
    void uncommentedArtifactIdStillSatisfiesTheRequiredPomPattern() throws Exception {
        EvalDefinition eval = evalRequiringPomPattern("<artifactId>spring-boot-starter-webmvc</artifactId>");
        Path workspace = workspaceWithPom("""
                <project>
                  <dependencies>
                    <!-- web starter -->
                    <dependency>
                      <artifactId>spring-boot-starter-webmvc</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);

        assertTrue(new MavenJudge().validatePolicy(eval, workspace) == null);
    }

    @Test
    void commentedOutSkipConfigurationDoesNotTripTheForbiddenBuildPatterns() throws Exception {
        Path evalDir = temp.resolve("eval-commented-skip");
        Files.createDirectories(evalDir.resolve("EVAL"));
        EvalDefinition eval = new EvalDefinition("boot/000-example", "boot", evalDir, Map.of());
        Path workspace = workspaceWithPom("""
                <project>
                  <!-- <properties><skipTests>true</skipTests></properties> -->
                </project>
                """);

        assertTrue(new MavenJudge().validatePolicy(eval, workspace) == null);
    }

    @Test
    void stripsTerminatedAndUnterminatedXmlComments() {
        assertTrue(MavenJudge.stripXmlComments("a<!-- x -->b<!-- y -->c").equals("abc"));
        assertTrue(MavenJudge.stripXmlComments("a<!-- never closed").equals("a"));
    }

    @Test
    void cdataSplicedCommentMarkersCannotHideActivePomContentFromForbiddenPatterns() throws Exception {
        Path evalDir = temp.resolve("eval-cdata");
        Files.createDirectories(evalDir.resolve("EVAL"));
        Files.writeString(evalDir.resolve("EVAL/checks.json"), """
                {"forbiddenPomPatterns": ["spring-boot-starter-web</artifactId>"]}
                """);
        EvalDefinition eval = new EvalDefinition("boot/000-example", "boot", evalDir, Map.of());
        // The two CDATA sections make "<!--" and "-->" inert character
        // data: Maven sees the dependency as live configuration. Naive
        // comment stripping would delete the whole span.
        Path workspace = workspaceWithPom("""
                <project>
                  <a><![CDATA[<!--]]></a>
                  <dependency><artifactId>spring-boot-starter-web</artifactId></dependency>
                  <b><![CDATA[-->]]></b>
                </project>
                """);

        var judgment = new MavenJudge().validatePolicy(eval, workspace);

        assertTrue(judgment != null && !judgment.pass());
        assertTrue(judgment.reasoning().contains("forbidden workaround found in pom"));
    }

    @Test
    void pomPolicyTextKeepsActiveContentAndDropsCommentsForWellFormedXml() {
        String text = MavenJudge.pomPolicyText("""
                <project>
                  <a><![CDATA[<!--]]></a>
                  <artifactId>keep-me</artifactId>
                  <b><![CDATA[-->]]></b>
                  <!-- <artifactId>drop-me</artifactId> -->
                </project>
                """);
        assertTrue(text.contains("<artifactId>keep-me</artifactId>"));
        assertFalse(text.contains("drop-me"));
    }

    private EvalDefinition evalRequiringPomPattern(String pattern) throws Exception {
        Path evalDir = temp.resolve("eval-" + Math.abs(pattern.hashCode()));
        Files.createDirectories(evalDir.resolve("EVAL"));
        Files.writeString(evalDir.resolve("EVAL/checks.json"), """
                {"requiredPomPatterns": ["%s"]}
                """.formatted(pattern.replace("\"", "\\\"")));
        return new EvalDefinition("boot/000-example", "boot", evalDir, Map.of());
    }

    private Path workspaceWithPom(String pom) throws Exception {
        Path workspace = temp.resolve("ws-" + Math.abs(pom.hashCode()));
        Files.createDirectories(workspace);
        Files.writeString(workspace.resolve("pom.xml"), pom);
        return workspace;
    }
}
