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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Docker sandbox mode: one benchmark image, fresh containers per
 * attempt. The agent CLI runs headless inside an agent container; the
 * judge's Maven build runs in a SEPARATE fresh container from the same
 * image, started only after the agent container is destroyed. Same
 * image means identical toolchains; separate containers mean nothing
 * the agent left behind (background processes, $HOME state such as
 * ~/.m2/settings.xml, environment) exists while hidden tests are
 * injected and judged. The candidate workspace is the only host
 * directory mounted writable; the host Maven caches are mounted
 * read-only and Maven reads through to them via maven.repo.local.tail
 * while writing to a container-local overlay.
 *
 * Isolation contract, do not weaken:
 * - Attempts run as the image's non-root user (Claude Code refuses
 *   --dangerously-skip-permissions under root).
 * - No host HOME, no host config dirs, no host credentials reach the
 *   container except the env the agent config declares, plus (codex
 *   only) the host ~/.codex/auth.json seeded into an otherwise empty
 *   CODEX_HOME.
 * - CLAUDE_CONFIG_DIR points at an empty container path baked into the
 *   image; each container starts with it fresh.
 * - The judge container is fresh: no agent env, no agent processes,
 *   a clean $HOME, and a Maven overlay freshly seeded from the
 *   read-only cache mounts, which cannot be tampered with from inside
 *   any container.
 * - The image tag is derived from the Dockerfile content hash, so a
 *   stale local image can never serve a changed Dockerfile.
 * - Network stays on: the open-book policy is unchanged.
 *
 * Cost and token metadata: the claude CLI reports them in its headless
 * JSON output and they are parsed from it; the other CLIs do not
 * expose them headlessly, so their docker-mode records carry null.
 *
 * This class is measurement-critical and is part of the ContentHashes
 * benchmark input list, together with harness/docker/Dockerfile.
 */
final class DockerSandbox {

    static final String IMAGE_NAME = "spring-evals-bench";
    static final String CACHE_REPOSITORY = "/opt/m2-cache/repository";
    static final String CACHE_WRAPPER = "/opt/m2-cache/wrapper";
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(30);
    // Grace period for the in-container `timeout` wrapper to fire before
    // the host-side backstop kills the docker client.
    private static final Duration HOST_GRACE = Duration.ofSeconds(90);

    private static final ObjectMapper JSON = new ObjectMapper();

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

    /**
     * --sandbox docker|host. Default is docker when `docker info`
     * succeeds, host otherwise. An explicit docker request must fail
     * loudly rather than silently degrade to host isolation.
     */
    static String resolveMode(String requested) {
        if (requested == null) {
            return dockerAvailable() ? "docker" : "host";
        }
        if (!requested.equals("docker") && !requested.equals("host")) {
            throw new IllegalArgumentException("--sandbox must be docker or host, got: " + requested);
        }
        if (requested.equals("docker") && !dockerAvailable()) {
            throw new IllegalStateException("--sandbox docker requested but `docker info` failed; start Docker first");
        }
        return requested;
    }

    /**
     * The image tag is content-addressed from the Dockerfile, so a
     * Dockerfile change always forces a rebuild and can never run
     * under a stale local image with the same name.
     */
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

    /**
     * Non-interactive CLI invocations, one per provider. Verified
     * against each CLI's --help inside the benchmark image.
     * - claude: --output-format json so cost, tokens, and the result
     *   text are machine-readable.
     * - codex: --dangerously-bypass-approvals-and-sandbox, because the
     *   container is the sandbox; codex's own sandbox would leave the
     *   bind-mounted workspace read-only.
     * - qwen-code: -m keeps the recorded model authoritative even if
     *   the agent config env omits OPENAI_MODEL.
     */
    static List<String> agentCommand(String provider, String model, String prompt) {
        return switch (provider) {
            case "claude" -> List.of("claude", "-p", prompt, "--model", model,
                    "--dangerously-skip-permissions", "--output-format", "json");
            case "codex" -> List.of("codex", "exec", "--skip-git-repo-check",
                    "--dangerously-bypass-approvals-and-sandbox", "-m", model, prompt);
            case "gemini" -> List.of("gemini", "-m", model, "-y", "-p", prompt);
            case "qwen-code" -> List.of("qwen", "-y", "-m", model, "-p", prompt);
            default -> throw new IllegalArgumentException(
                    "unknown provider '%s' (supported: claude, codex, gemini, qwen-code)".formatted(provider));
        };
    }

    /** Parsed claude headless JSON; null when the output is not that shape. */
    record ClaudeHeadlessResult(Double costUsd, Long inputTokens, Long outputTokens, String resultText) {
    }

    static ClaudeHeadlessResult parseClaudeJson(String output) {
        if (output == null) {
            return null;
        }
        // The JSON object is the last thing the CLI prints; tolerate
        // leading noise (npm warnings, progress lines).
        int start = output.indexOf('{');
        if (start < 0) {
            return null;
        }
        try {
            JsonNode node = JSON.readTree(output.substring(start));
            if (!node.isObject() || !node.has("result") && !node.has("total_cost_usd")) {
                return null;
            }
            JsonNode usage = node.get("usage");
            return new ClaudeHeadlessResult(
                    node.hasNonNull("total_cost_usd") ? node.get("total_cost_usd").asDouble() : null,
                    usage != null && usage.hasNonNull("input_tokens") ? usage.get("input_tokens").asLong() : null,
                    usage != null && usage.hasNonNull("output_tokens") ? usage.get("output_tokens").asLong() : null,
                    node.hasNonNull("result") ? node.get("result").asText() : null);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static String cliBinary(String provider) {
        return switch (provider) {
            case "claude" -> "claude";
            case "codex" -> "codex";
            case "gemini" -> "gemini";
            case "qwen-code" -> "qwen";
            default -> provider;
        };
    }

    /** CLI version as shipped in the image; prefixed so records show the sandbox mode. */
    static String cliVersion(String provider, String image) {
        ExecResult result = process(List.of("docker", "run", "--rm", image, cliBinary(provider), "--version"),
                Duration.ofMinutes(2));
        String first = result.output() == null ? "" : result.output().strip().lines().findFirst().orElse("");
        return "docker: " + (result.exitCode() == 0 && !first.isBlank() ? first : "unknown");
    }

    /** The container JDK version; docker-mode records must not claim the host JVM. */
    static String javaVersion(String image) {
        ExecResult result = process(List.of("docker", "run", "--rm", image, "sh", "-c",
                "java -version 2>&1 | head -1"), Duration.ofMinutes(2));
        var matcher = java.util.regex.Pattern.compile("\"([^\"]+)\"")
                .matcher(result.output() == null ? "" : result.output());
        return "docker: " + (result.exitCode() == 0 && matcher.find() ? matcher.group(1) : "unknown");
    }

    /**
     * Removes containers left behind by a DEAD harness process only.
     * Every container is labeled with its owner's pid; a container whose
     * owner is still alive belongs to a concurrently running lane and
     * must never be touched, or a free command could kill a paid
     * attempt mid-flight and record a false verdict.
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
     * Starts one container: workspace mounted writable at /workspace,
     * host Maven caches mounted read-only, env injected via a
     * mode-0600 env file that is deleted right after start (never
     * passed on a visible command line; it does land in the container
     * config, which is why stale containers are pruned and every
     * container is removed on close and on JVM shutdown). A write
     * probe fails fast when the container user cannot write the
     * bind-mounted workspace (e.g. Linux uid mismatch), so a
     * misconfigured host aborts loudly instead of recording fake
     * failures.
     */
    static Container start(Path workspace, Map<String, String> env, String image) {
        String name = "spring-evals-" + UUID.randomUUID();
        Path envFile = writeEnvFile(env);
        try {
            List<String> command = new ArrayList<>(List.of("docker", "run", "-d", "--name", name,
                    "--label", "spring-evals=1",
                    "--label", "spring-evals-owner-pid=" + ProcessHandle.current().pid(),
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
            // Credentials live in the container config; an interrupted
            // run must not leave them running on the docker daemon.
            this.shutdownHook = new Thread(() -> remove(name));
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        }

        String name() {
            return name;
        }

        /**
         * Runs argv in the container under coreutils `timeout`, so the
         * process tree dies inside the container on expiry. Exit code
         * 124 marks the timeout. A host-side backstop covers a wedged
         * docker client.
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

        /** Copies ONLY the host codex credential into the otherwise empty CODEX_HOME. */
        void seedCodexAuth(Path hostAuthJson) {
            if (!Files.isRegularFile(hostAuthJson)) {
                System.out.println("note: " + hostAuthJson + " not found; codex will run unauthenticated");
                return;
            }
            ExecResult cp = process(List.of("docker", "cp", hostAuthJson.toString(),
                    name + ":/sandbox/codex-home/auth.json"), SHORT_TIMEOUT);
            if (cp.exitCode() != 0) {
                throw new IllegalStateException("could not seed codex auth.json:\n" + tail(cp.output(), 2000));
            }
            // docker cp writes root-owned files; the attempt user must own it.
            ExecResult own = process(List.of("docker", "exec", "-u", "root", name, "sh", "-c",
                    "chown agent:agent /sandbox/codex-home/auth.json && chmod 600 /sandbox/codex-home/auth.json"),
                    SHORT_TIMEOUT);
            if (own.exitCode() != 0) {
                throw new IllegalStateException("could not fix codex auth.json ownership:\n" + tail(own.output(), 2000));
            }
        }

        /**
         * Seeds the container's writable Maven state from the
         * read-only cache mounts: fresh overlay repository, wrapper
         * distributions copied from the read-only mount.
         */
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
     * Free container readiness check for `doctor --sandbox docker`.
     * Proves: image builds and starts, all four CLIs execute, cache
     * mounts are visible, the workspace bind mount is writable, and
     * the Maven wrapper resolves a JVM and a Maven distribution inside
     * the container. What it cannot prove: a real model completing a
     * task; that needs a paid run.
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
            for (String provider : List.of("claude", "codex", "gemini", "qwen-code")) {
                ExecResult version = container.exec(List.of(cliBinary(provider), "--version"), SHORT_TIMEOUT);
                if (version.exitCode() == 0 && !version.output().isBlank()) {
                    System.out.println("  ✓ " + cliBinary(provider) + " --version: "
                            + version.output().strip().lines().findFirst().orElse(""));
                } else {
                    ok = false;
                    System.out.println("  ✗ " + cliBinary(provider) + " --version failed (exit "
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
                ? "Docker sandbox is ready. Full judge-in-container coverage: ./spring-evals validate --sandbox docker."
                : "Docker sandbox is NOT ready.");
        return ok;
    }

    private static ExecResult process(List<String> command, Duration timeout) {
        long started = System.currentTimeMillis();
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            // Read concurrently so a chatty child can never fill the pipe
            // and deadlock against waitFor.
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
