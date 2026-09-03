package dev.danvega.springevals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import dev.danvega.springevals.cli.AgentCli;

/**
 * Isolation contract, never weaken: the judge runs in a fresh container started
 * only after the agent container is destroyed, so agent-planted state cannot
 * survive into judging; only the workspace is mounted writable.
 */
final class DockerSandbox {

    static final String IMAGE_NAME = "spring-evals-bench";
    static final String CACHE_REPOSITORY = "/opt/m2-cache/repository";
    static final String CACHE_WRAPPER = "/opt/m2-cache/wrapper";
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(30);
    // Lets the in-container `timeout` fire before the host backstop kills the docker client.
    private static final Duration HOST_GRACE = Duration.ofSeconds(90);

    private DockerSandbox() {
    }

    record ExecResult(int exitCode, String output, boolean timedOut, long durationMs) {
    }

    static boolean dockerAvailable() {
        try {
            return process(List.of("docker", "info"), SHORT_TIMEOUT).exitCode() == 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Agents and the judge only ever run in containers; there is no host fallback to degrade into. */
    static void requireDocker() {
        if (!dockerAvailable()) {
            throw new IllegalStateException("Docker is required: agents and the judge run in containers, "
                    + "and `docker info` failed. Start Docker and retry.");
        }
    }

    /** Content-addressed: a stale local image can never serve a changed Dockerfile. */
    static String imageTag(Path repoRoot) {
        String hash = ContentHashes.tree(List.of(repoRoot.resolve("harness/docker/Dockerfile")));
        return IMAGE_NAME + ":" + hash.substring(0, 12);
    }

    static boolean imageExists(String image) {
        return process(List.of("docker", "image", "inspect", image), SHORT_TIMEOUT).exitCode() == 0;
    }

    /** Ensures the content-addressed image exists and returns its tag. */
    static String ensureImage(Path repoRoot) {
        String image = imageTag(repoRoot);
        if (imageExists(image)) {
            return image;
        }
        Path dockerDir = repoRoot.resolve("harness/docker");
        System.out.println("building benchmark image " + image + " (first run; this can take several minutes)...");
        ExecResult build = process(List.of("docker", "build", "-t", image,
                "-f", dockerDir.resolve("Dockerfile").toString(), dockerDir.toString()), BUILD_TIMEOUT);
        if (build.exitCode() != 0) {
            throw new IllegalStateException("docker build failed for " + image + ":\n" + tail(build.output(), 4000));
        }
        return image;
    }

    /** CLI version as shipped in the image; prefixed so records show the sandbox mode. */
    static String cliVersion(AgentCli cli, String image) {
        ExecResult result = process(List.of("docker", "run", "--rm", image, cli.binary(), "--version"),
                Duration.ofMinutes(2));
        String first = result.output() == null ? "" : result.output().strip().lines().findFirst().orElse("");
        return "docker: " + (result.exitCode() == 0 && !first.isBlank() ? first : "unknown");
    }

    /** The container JDK version; records must not claim the host JVM. */
    static String javaVersion(String image) {
        ExecResult result = process(List.of("docker", "run", "--rm", image, "sh", "-c",
                "java -version 2>&1 | head -1"), Duration.ofMinutes(2));
        var matcher = java.util.regex.Pattern.compile("\"([^\"]+)\"")
                .matcher(result.output() == null ? "" : result.output());
        return "docker: " + (result.exitCode() == 0 && matcher.find() ? matcher.group(1) : "unknown");
    }

    /**
     * Removes only containers whose owner pid is dead; a live lane's container
     * must never be touched or a free command could kill a paid attempt mid-flight.
     */
    static void pruneStaleContainers() {
        ExecResult list = process(List.of("docker", "ps", "-a", "--filter", "label=spring-evals=1",
                "--format", "{{.ID}} {{.Label \"spring-evals-owner-pid\"}}"), SHORT_TIMEOUT);
        if (list.exitCode() != 0 || list.output() == null) {
            return;
        }
        List<String> stale = list.output().strip().lines()
                .filter(line -> !line.isBlank())
                .map(line -> line.split("\\s+", 2))
                .filter(parts -> !ownerAlive(parts.length > 1 ? parts[1] : ""))
                .map(parts -> parts[0])
                .toList();
        if (stale.isEmpty()) {
            return;
        }
        System.out.println("removing " + stale.size() + " stale sandbox container(s) from an interrupted run");
        List<String> command = new ArrayList<>(List.of("docker", "rm", "-f"));
        command.addAll(stale);
        process(command, Duration.ofMinutes(2));
    }

    /** A blank or unparseable owner label counts as dead: those containers are prunable leaks. */
    static boolean ownerAlive(String pidLabel) {
        try {
            return ProcessHandle.of(Long.parseLong(pidLabel.strip())).map(ProcessHandle::isAlive).orElse(false);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Env goes through a mode-0600 env file deleted right after start, never a
     * visible command line; a write probe fails fast on a host uid mismatch.
     * host.docker.internal is mapped so local model servers on the host stay reachable.
     */
    static Container start(Path workspace, Map<String, String> env, String image) {
        String name = "spring-evals-" + UUID.randomUUID();
        Path envFile = writeEnvFile(env);
        try {
            List<String> command = new ArrayList<>(List.of("docker", "run", "-d", "--name", name,
                    "--label", "spring-evals=1",
                    "--label", "spring-evals-owner-pid=" + ProcessHandle.current().pid(),
                    "--add-host", "host.docker.internal:host-gateway",
                    "-v", workspace.toAbsolutePath() + ":/workspace",
                    "-w", "/workspace",
                    "--env-file", envFile.toString()));
            Path home = Path.of(System.getProperty("user.home"));
            if (Files.isDirectory(home.resolve(".m2/repository"))) {
                command.addAll(List.of("-v", home.resolve(".m2/repository") + ":" + CACHE_REPOSITORY + ":ro"));
            }
            if (Files.isDirectory(home.resolve(".m2/wrapper"))) {
                command.addAll(List.of("-v", home.resolve(".m2/wrapper") + ":" + CACHE_WRAPPER + ":ro"));
            }
            command.addAll(List.of(image, "sleep", "infinity"));
            ExecResult run = process(command, Duration.ofMinutes(2));
            if (run.exitCode() != 0) {
                throw new IllegalStateException("could not start sandbox container:\n" + tail(run.output(), 2000));
            }
            Container container = new Container(name);
            try {
                ExecResult probe = container.exec(List.of("sh", "-c",
                        "touch .spring-evals-write-probe && rm .spring-evals-write-probe"), SHORT_TIMEOUT);
                if (probe.exitCode() != 0) {
                    throw new IllegalStateException("container user cannot write the bind-mounted workspace "
                            + "(host uid mismatch?); refusing to run attempts that would record fake failures:\n"
                            + tail(probe.output(), 1000));
                }
                container.refreshMavenState();
                return container;
            } catch (RuntimeException e) {
                container.close();
                throw e;
            }
        } finally {
            try {
                Files.deleteIfExists(envFile);
            } catch (IOException ignored) {
            }
        }
    }

    private static Path writeEnvFile(Map<String, String> env) {
        try {
            Path file = Files.createTempFile("spring-evals-env-", ".list",
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            StringBuilder text = new StringBuilder();
            env.forEach((key, value) -> {
                if (key.contains("\n") || key.contains("=") || value.contains("\n")) {
                    throw new IllegalArgumentException("agent env variable not representable in an env file: " + key);
                }
                text.append(key).append('=').append(value).append('\n');
            });
            Files.writeString(file, text.toString());
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static final class Container implements AutoCloseable {

        private final String name;
        private final Thread shutdownHook;

        private Container(String name) {
            this.name = name;
            // Credentials live in the container config; an interrupted run must not leave it.
            this.shutdownHook = new Thread(() -> remove(name));
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        }

        String name() {
            return name;
        }

        /**
         * Coreutils `timeout` kills the process tree inside the container (exit
         * 124); the host-side backstop only covers a wedged docker client.
         */
        ExecResult exec(List<String> argv, Duration timeout) {
            List<String> command = new ArrayList<>(List.of("docker", "exec", "-w", "/workspace", name,
                    "timeout", "--kill-after=15", String.valueOf(Math.max(1, timeout.toSeconds()))));
            command.addAll(argv);
            long started = System.currentTimeMillis();
            ExecResult raw = process(command, timeout.plus(HOST_GRACE));
            boolean timedOut = raw.timedOut() || raw.exitCode() == 124;
            return new ExecResult(raw.exitCode(), raw.output(), timedOut, System.currentTimeMillis() - started);
        }

        /** Copies ONE host file into the container; a missing file is noted, never faked. */
        void seed(AgentCli.SeedFile seed) {
            if (!Files.isRegularFile(seed.hostPath())) {
                System.out.println("note: " + seed.hostPath() + " not found; nothing seeded at " + seed.containerPath());
                return;
            }
            ExecResult cp = process(List.of("docker", "cp", seed.hostPath().toString(),
                    name + ":" + seed.containerPath()), SHORT_TIMEOUT);
            if (cp.exitCode() != 0) {
                throw new IllegalStateException("could not seed " + seed.containerPath() + ":\n" + tail(cp.output(), 2000));
            }
            // docker cp writes root-owned files; the attempt user must own it.
            ExecResult own = process(List.of("docker", "exec", "-u", "root", name, "sh", "-c",
                    "chown agent:agent '" + seed.containerPath() + "' && chmod 600 '" + seed.containerPath() + "'"),
                    SHORT_TIMEOUT);
            if (own.exitCode() != 0) {
                throw new IllegalStateException("could not fix ownership of " + seed.containerPath() + ":\n"
                        + tail(own.output(), 2000));
            }
        }

        /** Reseeds writable Maven state from the tamper-proof read-only cache mounts. */
        void refreshMavenState() {
            ExecResult reset = exec(List.of("sh", "-c",
                    "rm -rf \"$HOME/.m2/repository\" \"$HOME/.m2/wrapper\""
                            + " && mkdir -p \"$HOME/.m2/repository\""
                            + " && { [ -d " + CACHE_WRAPPER + "/dists ] && cp -a " + CACHE_WRAPPER
                            + " \"$HOME/.m2/wrapper\" || true; }"),
                    Duration.ofMinutes(5));
            if (reset.exitCode() != 0) {
                throw new IllegalStateException("could not reset container Maven state:\n" + tail(reset.output(), 2000));
            }
        }

        /** Judge execution inside this container. Only ever used on a fresh judge container. */
        MavenJudge.BuildRunner buildRunner() {
            return (workspace, command, timeout) -> {
                ExecResult result = exec(command, timeout);
                return new MavenJudge.BuildResult(result.exitCode(), result.output(), result.timedOut());
            };
        }

        @Override
        public void close() {
            remove(name);
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM already shutting down; the hook itself removes the container.
            }
        }

        private static void remove(String name) {
            process(List.of("docker", "rm", "-f", name), SHORT_TIMEOUT);
        }
    }

    /**
     * Free readiness check: proves the container plumbing, not that a live
     * model can complete a task (that needs a paid run).
     */
    static boolean selfCheck(Path repoRoot, Path sampleProject) {
        System.out.println("Docker sandbox self-check (" + IMAGE_NAME + ")\n");
        if (!dockerAvailable()) {
            System.out.println("  ✗ `docker info` failed; start Docker first");
            return false;
        }
        System.out.println("  ✓ docker daemon reachable");
        String image;
        try {
            image = ensureImage(repoRoot);
        } catch (RuntimeException e) {
            System.out.println("  ✗ " + e.getMessage());
            return false;
        }
        System.out.println("  ✓ image present: " + image);

        boolean ok = true;
        Path ws;
        try {
            ws = Files.createTempDirectory("spring-evals-docker-check-");
            if (sampleProject != null) {
                Workspaces.copyTree(sampleProject, ws);
                Path mvnw = ws.resolve("mvnw");
                if (Files.exists(mvnw)) {
                    mvnw.toFile().setExecutable(true);
                }
            }
        } catch (IOException e) {
            System.out.println("  ✗ could not stage a sample workspace: " + e.getMessage());
            return false;
        }
        try (Container container = start(ws, Map.of(), image)) {
            System.out.println("  ✓ container starts; workspace bind mount is writable");
            for (AgentCli cli : AgentCli.all()) {
                ExecResult version = container.exec(List.of(cli.binary(), "--version"), SHORT_TIMEOUT);
                if (version.exitCode() == 0 && !version.output().isBlank()) {
                    System.out.println("  ✓ " + cli.binary() + " --version: "
                            + version.output().strip().lines().findFirst().orElse(""));
                } else {
                    ok = false;
                    System.out.println("  ✗ " + cli.binary() + " --version failed (exit "
                            + version.exitCode() + ")");
                }
            }
            ExecResult cache = container.exec(List.of("sh", "-c",
                    "ls " + CACHE_REPOSITORY + " 2>/dev/null | head -1"), SHORT_TIMEOUT);
            if (cache.exitCode() == 0 && !cache.output().isBlank()) {
                System.out.println("  ✓ read-only Maven cache mounted at " + CACHE_REPOSITORY);
            } else {
                System.out.println("  ! Maven cache mount is empty or missing; builds will download everything");
            }
            if (sampleProject == null) {
                System.out.println("  ! no evals in the catalog; skipped the in-container Maven wrapper check");
            } else {
                ExecResult maven = container.exec(List.of("./mvnw", "-B", "-ntp", "-v"), Duration.ofMinutes(5));
                if (maven.exitCode() == 0) {
                    System.out.println("  ✓ Maven wrapper runs in-container: "
                            + maven.output().strip().lines().findFirst().orElse(""));
                } else {
                    ok = false;
                    System.out.println("  ✗ ./mvnw -v failed in the container:\n" + tail(maven.output(), 2000));
                }
            }
        } catch (RuntimeException e) {
            System.out.println("  ✗ " + e.getMessage());
            ok = false;
        }
        System.out.println();
        System.out.println(ok
                ? "Docker sandbox is ready. Full judge-in-container coverage: ./spring-evals validate."
                : "Docker sandbox is NOT ready.");
        return ok;
    }

    private static ExecResult process(List<String> command, Duration timeout) {
        long started = System.currentTimeMillis();
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            // Read concurrently so a chatty child cannot fill the pipe and deadlock waitFor.
            java.util.concurrent.CompletableFuture<byte[]> output = java.util.concurrent.CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            return process.getInputStream().readAllBytes();
                        } catch (IOException e) {
                            return new byte[0];
                        }
                    });
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                String partial = output.getNow(new byte[0]).length == 0 ? ""
                        : new String(output.getNow(new byte[0]));
                return new ExecResult(-1, partial, true, System.currentTimeMillis() - started);
            }
            return new ExecResult(process.exitValue(), new String(output.join()), false,
                    System.currentTimeMillis() - started);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while running: " + String.join(" ", command), e);
        }
    }

    private static String tail(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(text.length() - max);
    }
}
