package dev.danvega.springevals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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

    /** Transcripts sit beside the workspaces, outside the repository; results record only the path and counts. */
    public Path transcriptsDir() {
        return runsDir.resolve("transcripts");
    }

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

    /**
     * Replace the workspace's src tree (and optionally pom) with a reference
     * candidate: SOLUTION, an ALTERNATIVES entry, or a WORKAROUNDS entry.
     */
    public void applyCandidate(Path candidateDir, Path ws) {
        deleteTree(ws.resolve("src"));
        copyTree(candidateDir.resolve("src"), ws.resolve("src"));
        Path candidatePom = candidateDir.resolve("pom.xml");
        if (Files.exists(candidatePom)) {
            try {
                Files.copy(candidatePom, ws.resolve("pom.xml"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** Remove any tests the agent wrote, then inject the hidden eval tests. */
    public void injectEvalTests(EvalDefinition eval, Path ws) {
        deleteWithin(ws, ws.resolve("src").resolve("test"));
        copyTree(eval.evalTestsDir(), ws);
        restoreTrustedMavenLauncher(eval, ws);
    }

    // The candidate pom stays intact (dependency changes are part of several
    // evals); MavenJudge separately rejects test-skipping configuration.
    private void restoreTrustedMavenLauncher(EvalDefinition eval, Path ws) {
        deleteWithin(ws, ws.resolve(".mvn"));
        Path trustedMvn = eval.projectDir().resolve(".mvn");
        if (Files.isDirectory(trustedMvn)) {
            copyTree(trustedMvn, ws.resolve(".mvn"));
        }
        for (String launcher : java.util.List.of("mvnw", "mvnw.cmd")) {
            Path source = eval.projectDir().resolve(launcher);
            if (Files.exists(source)) {
                try {
                    deleteWithin(ws, ws.resolve(launcher));
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

    /** Destinations are always real files and directories: a link or file in the way is replaced, never followed. */
    static void copyTree(Path source, Path target) {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    realDirectory(target, destination);
                } else {
                    realDirectory(target, destination.getParent());
                    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
                            && !Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
                        deleteTree(destination);
                    }
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Creates dir under root as real directories, replacing any link or file on the way. */
    private static void realDirectory(Path root, Path dir) throws IOException {
        Path current = root;
        for (Path component : root.relativize(dir)) {
            current = current.resolve(component);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                deleteTree(current);
            }
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectory(current);
            }
        }
    }

    /**
     * Deletes target inside root without ever following a link: if any path
     * component under root is a link, that link is removed instead, because
     * the deeper path would resolve wherever the agent pointed it.
     */
    static void deleteWithin(Path root, Path target) {
        Path current = root;
        for (Path component : root.relativize(target)) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                deleteTree(current);
                return;
            }
        }
        deleteTree(target);
    }

    /** A link is deleted as a link; its target, wherever it points, is never touched. */
    static void deleteTree(Path dir) {
        if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            if (Files.isSymbolicLink(dir) || !Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
                Files.delete(dir);
                return;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
