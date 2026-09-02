package dev.danvega.springevals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/** Stable content identities used to prevent stale result reuse. */
final class ContentHashes {

    private ContentHashes() {
    }

    static String eval(EvalDefinition eval) {
        return tree(List.of(eval.dir().resolve("eval.yaml"), eval.promptFile(), eval.projectDir(), eval.evalTestsDir()));
    }

    static String agent(Path repoRoot, String name) {
        return tree(List.of(repoRoot.resolve("agents").resolve(name + ".json")));
    }

    static String candidate(Path workspace) {
        return tree(List.of(workspace.resolve("pom.xml"), workspace.resolve("build.gradle"),
                workspace.resolve("build.gradle.kts"), workspace.resolve("settings.gradle"),
                workspace.resolve("settings.gradle.kts"), workspace.resolve("mvnw"),
                workspace.resolve(".mvn"), workspace.resolve("src/main")));
    }

    static String benchmark(Path repoRoot) {
        Path java = repoRoot.resolve("harness/src/main/java/dev/danvega/springevals");
        String hash = tree(List.of(java.resolve("Main.java"), java.resolve("Agents.java"), java.resolve("Workspaces.java"),
                java.resolve("MavenJudge.java"), java.resolve("EvalDefinition.java"),
                java.resolve("DockerSandbox.java"), java.resolve("RunScheduler.java"),
                java.resolve("cli"),
                repoRoot.resolve("harness/docker/Dockerfile"),
                repoRoot.resolve("harness/pom.xml"), repoRoot.resolve("spring-evals")));
        // Bump the prefix only in a batch that changes measurement behavior; see docs/VERSIONS.md.
        return "0.5.0+" + hash.substring(0, 12);
    }

    static String tree(List<Path> roots) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Path root : roots.stream().sorted().toList()) {
                if (!Files.exists(root)) {
                    continue;
                }
                if (Files.isRegularFile(root, LinkOption.NOFOLLOW_LINKS)) {
                    update(digest, root.getFileName().toString(), root);
                    continue;
                }
                try (Stream<Path> paths = Files.walk(root)) {
                    for (Path path : paths
                            .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                            .sorted().toList()) {
                        update(digest, root.getFileName() + "/" + root.relativize(path), path);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void update(MessageDigest digest, String relative, Path file) throws IOException {
        digest.update(relative.replace('\\', '/').getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(Files.readAllBytes(file));
        digest.update((byte) 0);
    }
}
