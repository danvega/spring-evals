package dev.danvega.springevals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Container plumbing checks. Self-skipping: they need a docker daemon
 * and the benchmark image, and are meaningless without them.
 */
class DockerSandboxContainerTest {

    @TempDir
    Path temp;

    private static String image() {
        // Tests run with the harness directory as the working directory.
        return DockerSandbox.imageTag(Path.of("").toAbsolutePath().getParent());
    }

    @Test
    void containerSeesWorkspaceEnvAndSterileConfigPaths() throws Exception {
        assumeTrue(DockerSandbox.dockerAvailable(), "docker daemon not available");
        assumeTrue(DockerSandbox.imageExists(image()), "benchmark image not built");

        Files.writeString(temp.resolve("marker.txt"), "workspace-visible");
        try (DockerSandbox.Container container = DockerSandbox.start(temp,
                Map.of("SPRING_EVALS_PROBE", "probe-value"), image())) {

            var marker = container.exec(java.util.List.of("cat", "marker.txt"), Duration.ofSeconds(30));
            assertEquals(0, marker.exitCode());
            assertTrue(marker.output().contains("workspace-visible"));

            var env = container.exec(java.util.List.of("sh", "-c",
                    "printf '%s|%s|%s' \"$SPRING_EVALS_PROBE\" \"$CLAUDE_CONFIG_DIR\" \"$CODEX_HOME\""),
                    Duration.ofSeconds(30));
            assertEquals(0, env.exitCode());
            assertEquals("probe-value|/sandbox/claude-config|/sandbox/codex-home", env.output().strip());

            var claudeConfig = container.exec(java.util.List.of("sh", "-c",
                    "ls -A /sandbox/claude-config /sandbox/codex-home"), Duration.ofSeconds(30));
            assertEquals(0, claudeConfig.exitCode());
            assertTrue(claudeConfig.output().strip().lines()
                    .filter(line -> !line.isBlank() && !line.endsWith(":"))
                    .findAny().isEmpty(), "sterile config dirs must start empty, saw: " + claudeConfig.output());

            var host = container.exec(java.util.List.of("sh", "-c",
                    "test -e /workspace && echo yes"), Duration.ofSeconds(30));
            assertTrue(host.output().contains("yes"));
        }
    }

    @Test
    void pruneSparesContainersOwnedByALiveProcessAndRemovesDeadOwnedOnes() throws Exception {
        assumeTrue(DockerSandbox.dockerAvailable(), "docker daemon not available");
        assumeTrue(DockerSandbox.imageExists(image()), "benchmark image not built");

        String liveOwned = "spring-evals-test-live-" + java.util.UUID.randomUUID();
        String deadOwned = "spring-evals-test-dead-" + java.util.UUID.randomUUID();
        try {
            run("docker", "run", "-d", "--name", liveOwned, "--label", "spring-evals=1",
                    "--label", "spring-evals-owner-pid=" + ProcessHandle.current().pid(),
                    image(), "sleep", "infinity");
            run("docker", "run", "-d", "--name", deadOwned, "--label", "spring-evals=1",
                    "--label", "spring-evals-owner-pid=999999999",
                    image(), "sleep", "infinity");

            DockerSandbox.pruneStaleContainers();

            assertTrue(exists(liveOwned), "a live lane's container must survive the prune");
            assertTrue(!exists(deadOwned), "a dead owner's container must be pruned");
        } finally {
            run("docker", "rm", "-f", liveOwned);
            run("docker", "rm", "-f", deadOwned);
        }
    }

    private static void run(String... argv) throws Exception {
        Process process = new ProcessBuilder(argv).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        process.waitFor();
    }

    private static boolean exists(String name) throws Exception {
        Process process = new ProcessBuilder("docker", "container", "inspect", name)
                .redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        return process.waitFor() == 0;
    }

    @Test
    void inContainerTimeoutKillsTheProcessAndReportsTimedOut() throws Exception {
        assumeTrue(DockerSandbox.dockerAvailable(), "docker daemon not available");
        assumeTrue(DockerSandbox.imageExists(image()), "benchmark image not built");

        try (DockerSandbox.Container container = DockerSandbox.start(temp, Map.of(), image())) {
            var result = container.exec(java.util.List.of("sleep", "30"), Duration.ofSeconds(2));
            assertTrue(result.timedOut());
            // The container must remain usable for the judge phase.
            var after = container.exec(java.util.List.of("echo", "alive"), Duration.ofSeconds(30));
            assertEquals(0, after.exitCode());
            assertTrue(after.output().contains("alive"));
        }
    }
}
