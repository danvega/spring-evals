package dev.danvega.springevals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Candidate workspaces live outside the repository so SOLUTION and EVAL
 * directories are never adjacent to the agent's working directory.
 */
public class Workspaces {

    private final Path runsDir;

    public Workspaces(Path repoRoot) {
        String configured = System.getenv("SPRING_EVALS_RUNS_DIR");
        this.runsDir = configured == null || configured.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "spring-evals-runs")
                : Path.of(configured).toAbsolutePath().normalize();
    }

    /** Agent-steering context files; none may exist in a candidate workspace. */
    private static final java.util.List<String> AGENT_CONTEXT_FILES = java.util.List.of(
            "CLAUDE.md", "AGENTS.md", "GEMINI.md", "QWEN.md", ".claude", ".mcp.json",
            ".cursorrules", ".cursor", ".github/copilot-instructions.md");

    public Path freshCopy(EvalDefinition eval, String label) {
        Path ws = runsDir.resolve(eval.id().replace('/', '-') + "-" + label + "-" + UUID.randomUUID());
        copyTree(eval.projectDir(), ws);
        for (String contextFile : AGENT_CONTEXT_FILES) {
            deleteTree(ws.resolve(contextFile));
        }
        Path mvnw = ws.resolve("mvnw");
        if (Files.exists(mvnw)) {
            mvnw.toFile().setExecutable(true);
        }
        return ws;
    }

    /** Replace the workspace's src tree (and optionally pom) with the reference solution. */
    public void applySolution(EvalDefinition eval, Path ws) {
        deleteTree(ws.resolve("src"));
        copyTree(eval.solutionDir().resolve("src"), ws.resolve("src"));
        Path solutionPom = eval.solutionDir().resolve("pom.xml");
        if (Files.exists(solutionPom)) {
            try {
                Files.copy(solutionPom, ws.resolve("pom.xml"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** Remove any tests the agent wrote, then inject the hidden eval tests. */
    public void injectEvalTests(EvalDefinition eval, Path ws) {
        deleteTree(ws.resolve("src").resolve("test"));
        copyTree(eval.evalTestsDir(), ws);
        restoreTrustedMavenLauncher(eval, ws);
    }

    // The candidate pom stays intact (dependency changes are part of several
    // evals); MavenJudge separately rejects test-skipping configuration.
    private void restoreTrustedMavenLauncher(EvalDefinition eval, Path ws) {
        deleteTree(ws.resolve(".mvn"));
        Path trustedMvn = eval.projectDir().resolve(".mvn");
        if (Files.isDirectory(trustedMvn)) {
            copyTree(trustedMvn, ws.resolve(".mvn"));
        }
        for (String launcher : java.util.List.of("mvnw", "mvnw.cmd")) {
            Path source = eval.projectDir().resolve(launcher);
            if (Files.exists(source)) {
                try {
                    deleteTree(ws.resolve(launcher));
                    Files.copy(source, ws.resolve(launcher), StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        Path mvnw = ws.resolve("mvnw");
        if (Files.exists(mvnw)) {
            mvnw.toFile().setExecutable(true);
        }
    }

    static void copyTree(Path source, Path target) {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void deleteTree(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
