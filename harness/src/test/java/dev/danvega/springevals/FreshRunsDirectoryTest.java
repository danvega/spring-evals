package dev.danvega.springevals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** A fresh clone, a new SPRING_EVALS_RUNS_DIR, or a purged temp directory must not abort the first workspace copy. */
class FreshRunsDirectoryTest {

    @TempDir
    Path temp;

    @Test
    void freshCopyCreatesARunsDirectoryThatDoesNotExistYet() throws Exception {
        Path evalDir = Files.createDirectories(temp.resolve("evals/boot/000-example"));
        Files.createDirectories(evalDir.resolve("project/src/main/java"));
        Files.writeString(evalDir.resolve("project/pom.xml"), "<project/>");
        Files.writeString(evalDir.resolve("project/src/main/java/A.java"), "class A {}");
        EvalDefinition eval = new EvalDefinition("boot/000-example", "boot", evalDir, Map.of());

        Path runs = temp.resolve("missing/spring-evals-runs");
        Path ws = new Workspaces(temp, runs).freshCopy(eval, "test");

        assertTrue(ws.startsWith(runs));
        assertTrue(Files.isRegularFile(ws.resolve("pom.xml")));
        assertTrue(Files.isRegularFile(ws.resolve("src/main/java/A.java")));
    }

    @Test
    void copyTreeCreatesAMissingDestinationChain() throws Exception {
        Path source = Files.createDirectories(temp.resolve("source/dir"));
        Files.writeString(source.resolve("f.txt"), "x");
        Path target = temp.resolve("a/b/c/target");
        Workspaces.copyTree(temp.resolve("source"), target);
        assertTrue(Files.isRegularFile(target.resolve("dir/f.txt")));
    }
}
