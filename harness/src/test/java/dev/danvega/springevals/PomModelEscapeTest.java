package dev.danvega.springevals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every pom check reads the one root pom, so configuration must not be able to live anywhere else. */
class PomModelEscapeTest {

    @TempDir
    Path workspace;

    private static String pom(String body) {
        return """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    %s
                </project>
                """.formatted(body);
    }

    @Test
    void plainPomWithCentralParentPasses() throws Exception {
        String body = """
                <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>4.1.1</version>
                    <relativePath/>
                </parent>
                <build><plugins><plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                </plugin></plugins></build>
                """;
        assertNull(MavenJudge.pomModelEscape(pom(body), workspace));
    }

    @Test
    void workspaceLocalParentIsRefused() throws Exception {
        Files.createDirectories(workspace.resolve("parent"));
        Files.writeString(workspace.resolve("parent/pom.xml"), pom("<packaging>pom</packaging>"));
        String body = """
                <parent>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <relativePath>parent/pom.xml</relativePath>
                </parent>
                """;
        String reason = MavenJudge.pomModelEscape(pom(body), workspace);
        assertNotNull(reason);
        assertTrue(reason.contains("<parent>") || reason.contains("second pom.xml"), reason);
    }

    @Test
    void secondPomAnywhereInTheWorkspaceIsRefusedEvenWithoutAParent() throws Exception {
        Files.createDirectories(workspace.resolve("lib"));
        Files.writeString(workspace.resolve("lib/pom.xml"), pom(""));
        String reason = MavenJudge.pomModelEscape(pom(""), workspace);
        assertNotNull(reason);
        assertTrue(reason.contains("second pom.xml"), reason);
    }

    @Test
    void buildOutputPomsDoNotCount() throws Exception {
        Files.createDirectories(workspace.resolve("target/classes/META-INF/maven/com.example/demo"));
        Files.writeString(workspace.resolve("target/classes/META-INF/maven/com.example/demo/pom.xml"), pom(""));
        assertNull(MavenJudge.pomModelEscape(pom(""), workspace));
    }

    @Test
    void modulesRepositoriesAndBuildExtensionsAreRefused() throws Exception {
        assertNotNull(MavenJudge.pomModelEscape(pom("<modules><module>a</module></modules>"), workspace));
        assertNotNull(MavenJudge.pomModelEscape(pom("<repositories><repository><id>x</id>"
                + "<url>file://${project.basedir}/repo</url></repository></repositories>"), workspace));
        assertNotNull(MavenJudge.pomModelEscape(pom("<profiles><profile><id>p</id><pluginRepositories>"
                + "<pluginRepository><id>x</id><url>file:///tmp/r</url></pluginRepository>"
                + "</pluginRepositories></profile></profiles>"), workspace));
        assertNotNull(MavenJudge.pomModelEscape(pom("<build><extensions><extension><groupId>g</groupId>"
                + "<artifactId>a</artifactId><version>1</version></extension></extensions></build>"), workspace));
    }

    @Test
    void interpolatedPluginAndParentCoordinatesAreRefused() throws Exception {
        String plugin = """
                <properties><dep>maven-dependency-plugin</dep></properties>
                <build><plugins><plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>${dep}</artifactId>
                    <configuration><includeScope>test</includeScope></configuration>
                </plugin></plugins></build>
                """;
        assertNotNull(MavenJudge.pomModelEscape(pom(plugin), workspace));
        String parent = """
                <properties><p>parent</p></properties>
                <parent>
                    <groupId>com.example</groupId>
                    <artifactId>${p}</artifactId>
                    <version>1</version>
                    <relativePath/>
                </parent>
                """;
        assertNotNull(MavenJudge.pomModelEscape(pom(parent), workspace));
    }

    @Test
    void attributeOnATagDoesNotHideItFromTheBuildConfigPatterns() throws Exception {
        Files.writeString(workspace.resolve("pom.xml"), pom("""
                <build><plugins><plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-dependency-plugin</artifactId>
                    <configuration combine.self="override"><includeScope>test</includeScope></configuration>
                </plugin></plugins></build>
                """));
        Judgment verdict = new MavenJudge().validatePolicy(eval(), workspace);
        assertNotNull(verdict);
        assertTrue(verdict.reasoning().contains("suppress or redirect"), verdict.reasoning());
        Files.writeString(workspace.resolve("pom.xml"), pom("""
                <build><plugins><plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration><skipTests combine.self="override">true</skipTests></configuration>
                </plugin></plugins></build>
                """));
        assertNotNull(new MavenJudge().validatePolicy(eval(), workspace));
    }

    private EvalDefinition eval() throws Exception {
        Path evalDir = Files.createDirectories(workspace.resolveSibling(workspace.getFileName() + "-eval"));
        Files.createDirectories(evalDir.resolve("project"));
        Files.createDirectories(evalDir.resolve("EVAL"));
        return new EvalDefinition("boot/000-example", "boot", evalDir, java.util.Map.of());
    }
}
