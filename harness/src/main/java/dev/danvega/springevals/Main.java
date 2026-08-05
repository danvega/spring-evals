package dev.danvega.springevals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springaicommunity.agents.client.AgentClient;
import org.springaicommunity.agents.client.AgentClientResponse;
import org.springaicommunity.agents.model.AgentModel;
import org.springaicommunity.judge.result.Judgment;

import dev.danvega.springevals.Agents.AgentSpec;
import dev.danvega.springevals.ResultStore.RunRecord;

/** Entry point behind ./spring-evals; --sandbox defaults to docker when available. */
public class Main {

    private final Path root;
    private final EvalCatalog catalog;
    private final Workspaces workspaces;
    private final MavenJudge mavenJudge = new MavenJudge();
    private final Agents agents;
    private final ResultStore resultStore;
    private final Reports reports;

    Main(Path root) {
        this.root = root;
        this.catalog = new EvalCatalog(root);
        this.workspaces = new Workspaces(root);
        this.agents = new Agents(root);
        this.resultStore = new ResultStore(root);
        this.reports = new Reports(root, catalog);
    }

    public static void main(String[] args) throws Exception {
        Main main = new Main(findRepoRoot());
        String command = args.length > 0 ? args[0] : "";
        Map<String, String> opts = parseOptions(args);
        List<String> positionals = parsePositionals(args);

        switch (command) {
            case "list" -> main.list();
            case "validate" -> System.exit(main.validate(positionals, opts) ? 0 : 1);
            case "doctor" -> System.exit(main.doctor(opts));
            case "run" -> main.run(opts);
            case "report" -> main.reports.print(main.resultStore.load());
            case "estimate" -> main.estimate(opts);
            case "serve" -> DashboardServer.serve(main.root, opts);
            default -> {
                System.out.println("usage: ./spring-evals <list|validate|doctor|run|report|estimate|serve>");
                System.exit(command.isEmpty() ? 0 : 1);
            }
        }
        // Agent CLI SDKs can leave non-daemon threads behind; exit explicitly.
        System.exit(0);
    }

    /** Expected assumes ~1.7 attempts per eval; worst case assumes every attempt is used. */
    void estimate(Map<String, String> opts) {
        int attempts = Integer.parseInt(opts.getOrDefault("attempts", "1"));
        List<EvalDefinition> evals = selectTargets(opts);
        // No selector mirrors run --all-agents (enabled only), so the projection matches a run.
        List<AgentSpec> specs = opts.containsKey("agent") || opts.containsKey("family") || opts.containsKey("all-agents")
                ? resolveAgents(opts)
                : agents.loadAll().stream().filter(AgentSpec::enabled).toList();

        double expectedAttempts = attempts == 1 ? 1.0 : 1.7;
        double totalExpected = 0;
        double totalWorst = 0;
        System.out.printf("%n%-24s %14s %14s %14s%n", "agent", "$/attempt (est)", "expected", "worst case");
        for (AgentSpec spec : specs) {
            if (spec.estCostPerAttemptUsd() == null) {
                System.out.printf("%-24s %14s %14s %14s%n", spec.name(), "n/a", "n/a", "n/a");
                continue;
            }
            double perAttempt = spec.estCostPerAttemptUsd();
            double expected = evals.size() * expectedAttempts * perAttempt;
            double worst = evals.size() * attempts * perAttempt;
            totalExpected += expected;
            totalWorst += worst;
            System.out.printf("%-24s %14s %14s %14s%n", spec.name(),
                    "$%.2f".formatted(perAttempt), "$%.2f".formatted(expected), "$%.2f".formatted(worst));
        }
        System.out.printf("%-24s %14s %14s %14s%n", "TOTAL", "",
                "$%.2f".formatted(totalExpected), "$%.2f".formatted(totalWorst));
        System.out.printf("%n%d evals, up to %d attempts each. Estimates come from estCostPerAttemptUsd in agents/*.json.%n",
                evals.size(), attempts);
        System.out.println("Actual spend is recorded per attempt in results/results.json where the CLI reports it.");
    }

    void list() {
        String currentProject = null;
        for (EvalDefinition eval : catalog.all()) {
            if (!eval.project().equals(currentProject)) {
                currentProject = eval.project();
                System.out.println("\nspring-" + currentProject + ":");
            }
            System.out.println("  " + eval.summaryLine());
        }
    }

    int doctor(Map<String, String> opts) {
        // Opt-in: a first-use image build takes minutes a plain doctor call must not trigger.
        boolean dockerCheckFailed = false;
        if ("docker".equals(opts.get("sandbox"))) {
            List<EvalDefinition> all = catalog.all();
            dockerCheckFailed = !DockerSandbox.selfCheck(root, all.isEmpty() ? null : all.get(0).projectDir());
            System.out.println();
        }
        List<String> names;
        if (opts.containsKey("agent")) {
            names = List.of(opts.get("agent").split(","));
        } else if (opts.containsKey("family")) {
            names = agents.names().stream().filter(name -> name.startsWith(opts.get("family"))).toList();
            if (names.isEmpty()) {
                throw new IllegalArgumentException("no agents match family '" + opts.get("family") + "'");
            }
        } else {
            names = agents.names();
        }

        List<AgentSpec> specs = new ArrayList<>();
        int invalid = 0;
        for (String name : names) {
            try {
                specs.add(agents.load(name));
            } catch (RuntimeException e) {
                invalid++;
                System.out.println("BLOCKED   " + name);
                System.out.println("  ✗ invalid agent config: " + e.getMessage());
                System.out.println();
            }
        }
        int result = specs.isEmpty() ? 0 : new AgentDoctor().print(specs);
        if (invalid > 0) {
            System.out.println("Additionally, " + invalid + " agent config file(s) could not be parsed.");
        }
        return dockerCheckFailed || invalid > 0 ? 1 : result;
    }

    boolean validate(List<String> ids, Map<String, String> opts) {
        String sandbox = DockerSandbox.resolveMode(opts.get("sandbox"));
        String image = null;
        if ("docker".equals(sandbox)) {
            DockerSandbox.pruneStaleContainers();
            image = DockerSandbox.ensureImage(root);
        }
        System.out.println(sandboxBanner(sandbox, image));
        List<EvalDefinition> targets = ids.isEmpty()
                ? catalog.all()
                : ids.stream().map(catalog::load).toList();
        boolean ok = true;

        for (EvalDefinition eval : targets) {
            System.out.println("\n=== validate: " + eval.id() + " ===");

            List<String> structuralErrors = catalog.validate(eval);
            if (!structuralErrors.isEmpty()) {
                ok = false;
                structuralErrors.forEach(error -> System.out.println("   ✗ " + error));
                continue;
            }

            System.out.println("1/2 broken project must FAIL the hidden tests...");
            Path brokenWs = workspaces.freshCopy(eval, "validate-broken");
            workspaces.injectEvalTests(eval, brokenWs);
            Judgment broken = judgeWorkspace(image, eval, brokenWs, true);
            if (broken.pass()) {
                ok = false;
                System.out.println("   ✗ broken project unexpectedly PASSED (" + brokenWs + ")");
            } else {
                String actualFailure = failureKind(new AgentRun(0L, null, null, null, null, null, null), broken,
                        brokenWs, false);
                String expectedFailure = eval.meta().get("baseline_failure");
                if (!expectedFailure.equals(actualFailure)) {
                    ok = false;
                    System.out.println("   ✗ baseline failed for the wrong reason: expected " + expectedFailure
                            + ", got " + actualFailure);
                } else {
                    System.out.println("   ✓ fails as expected (" + actualFailure + ")");
                }
            }

            System.out.println("2/2 reference solution must PASS the hidden tests...");
            Path solutionWs = workspaces.freshCopy(eval, "validate-solution");
            workspaces.applySolution(eval, solutionWs);
            workspaces.injectEvalTests(eval, solutionWs);
            Judgment solution = judgeWorkspace(image, eval, solutionWs, false);
            if (!solution.pass()) {
                ok = false;
                System.out.println("   ✗ solution FAILED: " + solution.reasoning());
                System.out.println("     see " + solutionWs.resolve("maven-output.log"));
            } else {
                System.out.println("   ✓ passes");
            }
        }

        System.out.println(ok ? "\nAll validations passed." : "\nValidation FAILED.");
        return ok;
    }

    private static String sandboxBanner(String sandbox, String image) {
        return "docker".equals(sandbox)
                ? "Sandbox mode: docker (agent and judge run in fresh per-attempt containers from " + image + ")"
                : "Sandbox mode: host (process-level isolation via EnvSandbox)";
    }

    /** Judges one workspace; a non-null image means docker mode with a fresh, env-free judge container. */
    private Judgment judgeWorkspace(String image, EvalDefinition eval, Path ws, boolean behaviorOnly) {
        if (image == null) {
            return behaviorOnly ? mavenJudge.judgeBehaviorOnly(eval, ws) : mavenJudge.judge(eval, ws);
        }
        try (DockerSandbox.Container container = DockerSandbox.start(ws, Map.of(), image)) {
            return behaviorOnly
                    ? mavenJudge.judgeBehaviorOnly(eval, ws, container.buildRunner())
                    : mavenJudge.judge(eval, ws, container.buildRunner());
        }
    }

    /** --family matches the agent-name prefix; explicit --agent picks override the enabled flag. */
    private List<AgentSpec> resolveAgents(Map<String, String> opts) {
        if (opts.containsKey("agent")) {
            List<AgentSpec> picked = List.of(opts.get("agent").split(",")).stream().map(agents::load).toList();
            picked.stream().filter(spec -> !spec.enabled()).forEach(spec -> System.out.printf(
                    "note: %s has \"enabled\": false in agents/%s.json; running it anyway because you named it%n",
                    spec.name(), spec.name()));
            return picked;
        }
        if (opts.containsKey("family")) {
            List<AgentSpec> family = agents.loadAll().stream()
                    .filter(AgentSpec::enabled)
                    .filter(spec -> spec.name().startsWith(opts.get("family")))
                    .toList();
            if (family.isEmpty()) {
                throw new IllegalArgumentException("no enabled agents match family '" + opts.get("family") + "'");
            }
            return family;
        }
        if (opts.containsKey("all-agents")) {
            List<AgentSpec> enabled = agents.loadAll().stream().filter(AgentSpec::enabled).toList();
            long disabled = agents.loadAll().size() - enabled.size();
            if (disabled > 0) {
                System.out.printf("skipping %d disabled agent(s); set \"enabled\": true in agents/<name>.json to include them%n",
                        disabled);
            }
            return enabled;
        }
        throw new IllegalArgumentException(
                "pick agents with --agent <name[,name...]>, --family <prefix>, or --all-agents");
    }

    void run(Map<String, String> opts) throws Exception {
        String sandbox = DockerSandbox.resolveMode(opts.get("sandbox"));
        List<AgentSpec> specs = resolveAgents(opts);
        int attempts = Integer.parseInt(opts.getOrDefault("attempts", "1"));
        if (attempts < 1 || attempts > 10) {
            throw new IllegalArgumentException("--attempts must be between 1 and 10");
        }
        boolean force = opts.containsKey("force");

        List<EvalDefinition> targets = selectTargets(opts);
        double projectedMaximum = specs.stream()
                .mapToDouble(spec -> spec.estCostPerAttemptUsd() == null ? 0 : spec.estCostPerAttemptUsd())
                .sum() * targets.size() * attempts;
        boolean paid = specs.stream().anyMatch(spec -> spec.estCostPerAttemptUsd() == null
                || spec.estCostPerAttemptUsd() > 0);
        double costCap = Double.POSITIVE_INFINITY;
        if (paid) {
            List<String> unknownCost = specs.stream().filter(spec -> spec.estCostPerAttemptUsd() == null)
                    .map(AgentSpec::name).toList();
            if (!unknownCost.isEmpty()) {
                throw new IllegalArgumentException("paid execution requires estCostPerAttemptUsd for every agent: "
                        + unknownCost);
            }
            if (!opts.containsKey("allow-paid-run")) {
                throw new IllegalArgumentException("paid model execution is disabled by default. Projected maximum: $%.2f. "
                        .formatted(projectedMaximum)
                        + "Review it with `estimate`, then explicitly pass --allow-paid-run and --max-total-cost <usd>.");
            }
            if (!opts.containsKey("max-total-cost")) {
                throw new IllegalArgumentException("paid runs require --max-total-cost <usd>");
            }
            costCap = Double.parseDouble(opts.get("max-total-cost"));
            if (!Double.isFinite(costCap) || costCap <= 0) {
                throw new IllegalArgumentException("--max-total-cost must be a positive finite dollar amount");
            }
            System.out.printf("Paid run authorized: projected maximum $%.2f; estimated campaign cap $%.2f.%n",
                    projectedMaximum, costCap);
        }

        // The chosen mode's isolation mechanism must be proven before any money is spent.
        String image = null;
        if ("docker".equals(sandbox)) {
            DockerSandbox.pruneStaleContainers();
            image = DockerSandbox.ensureImage(root);
            if (specs.stream().anyMatch(spec -> "claude".equals(spec.provider()) && spec.budgetUsd() != null)) {
                System.out.println("note: docker mode cannot enforce per-attempt budgetUsd; "
                        + "the campaign cap uses per-attempt estimates plus claude-reported actuals");
            }
        } else {
            EnvSandbox.selfTest();
        }
        System.out.println(sandboxBanner(sandbox, image));
        String recordedJavaVersion = image == null
                ? System.getProperty("java.version")
                : DockerSandbox.javaVersion(image);

        List<RunRecord> results = resultStore.load();
        double reservedCost = 0;
        String campaignId = resolveRunName(opts.get("run-name"), results);
        System.out.println("Run name: " + campaignId);
        String benchmarkVersion = benchmarkVersion();
        Map<String, String> cliVersions = new HashMap<>();

        String benchmarkImage = image;
        for (AgentSpec spec : specs) {
            // "docker:"-prefixed versions keep host- and docker-mode records
            // from ever satisfying each other's cache.
            String cliVersion = cliVersions.computeIfAbsent(spec.provider(),
                    provider -> benchmarkImage != null
                            ? DockerSandbox.cliVersion(provider, benchmarkImage)
                            : detectCliVersion(provider));
            for (EvalDefinition eval : targets) {
                String evalHash = ContentHashes.eval(eval);
                String agentHash = ContentHashes.agent(root, spec.name());
                List<RunRecord> existing = results.stream()
                        .filter(r -> r.agent().equals(spec.name()) && r.eval().equals(eval.id()))
                        .filter(r -> evalHash.equals(r.evalHash()) && agentHash.equals(r.agentConfigHash()))
                        .filter(r -> benchmarkVersion.equals(r.benchmarkVersion()))
                        .filter(r -> cliVersion.equals(r.cliVersion()))
                        .filter(r -> recordedJavaVersion.equals(r.javaVersion()))
                        .filter(r -> System.getProperty("os.name").equals(r.osName()))
                        .filter(r -> System.getProperty("os.arch").equals(r.osArch()))
                        .toList();

                if (force) {
                    results = new ArrayList<>(results.stream()
                            .filter(r -> !(r.agent().equals(spec.name()) && r.eval().equals(eval.id())
                                    && evalHash.equals(r.evalHash()) && agentHash.equals(r.agentConfigHash())
                                    && benchmarkVersion.equals(r.benchmarkVersion())
                                    && cliVersion.equals(r.cliVersion())
                                    && recordedJavaVersion.equals(r.javaVersion())
                                    && System.getProperty("os.name").equals(r.osName())
                                    && System.getProperty("os.arch").equals(r.osArch())))
                            .toList());
                } else if (existing.stream().anyMatch(RunRecord::passed) || existing.size() >= attempts) {
                    System.out.printf("skip %s (%s): already %s — use --force to rerun%n", eval.id(), spec.name(),
                            existing.stream().anyMatch(RunRecord::passed) ? "passed" : "exhausted");
                    continue;
                }

                int done = force ? 0 : existing.size();
                for (int attempt = done + 1; attempt <= attempts; attempt++) {
                    double reservation = spec.estCostPerAttemptUsd() == null
                            ? Double.POSITIVE_INFINITY : spec.estCostPerAttemptUsd();
                    if (paid && reservedCost + reservation > costCap) {
                        System.out.printf("Cost cap reached before %s / %s attempt %d. Reserved $%.2f of $%.2f.%n",
                                eval.id(), spec.name(), attempt, reservedCost, costCap);
                        reports.print(results);
                        return;
                    }
                    reservedCost += reservation;
                    System.out.printf("%n=== %s / %s / attempt %d/%d ===%n", eval.id(), spec.name(), attempt,
                            attempts);
                    Path ws = workspaces.freshCopy(eval, spec.name() + "-a" + attempt);
                    String baselineHash = ContentHashes.candidate(ws);

                    AgentRun agentRun;
                    Judgment verdict;
                    String candidateHash;
                    boolean untouched;
                    if (benchmarkImage != null) {
                        // The agent container is destroyed before hidden tests are injected, so
                        // nothing it left running or wrote outside the workspace touches judging.
                        try (DockerSandbox.Container agentContainer = DockerSandbox.start(ws,
                                Agents.expandAll(spec.env()), benchmarkImage)) {
                            if ("codex".equals(spec.provider())) {
                                agentContainer.seedCodexAuth(Path.of(System.getProperty("user.home"),
                                        ".codex", "auth.json"));
                            }
                            System.out.println("running agent CLI in container " + agentContainer.name() + "...");
                            agentRun = runAgentInContainer(agentContainer, spec, eval);
                        }
                        candidateHash = ContentHashes.candidate(ws);
                        untouched = baselineHash.equals(candidateHash);
                        System.out.println("injecting hidden tests, judging with ./mvnw clean test in a fresh container...");
                        workspaces.injectEvalTests(eval, ws);
                        try (DockerSandbox.Container judgeContainer = DockerSandbox.start(ws,
                                Map.of(), benchmarkImage)) {
                            verdict = mavenJudge.judge(eval, ws, judgeContainer.buildRunner());
                        }
                    } else {
                        System.out.println("running agent through AgentClient...");
                        agentRun = runAgent(spec, eval, ws);
                        candidateHash = ContentHashes.candidate(ws);
                        untouched = baselineHash.equals(candidateHash);
                        System.out.println("injecting hidden tests, judging with ./mvnw clean test...");
                        workspaces.injectEvalTests(eval, ws);
                        verdict = mavenJudge.judge(eval, ws);
                    }
                    if (agentRun.costUsd() != null && agentRun.costUsd() > reservation) {
                        reservedCost += agentRun.costUsd() - reservation;
                    }

                    String failureKind = verdict.pass() ? null : failureKind(agentRun, verdict, ws, untouched);
                    String failureReason = verdict.pass() ? null
                            : agentRun.error() != null ? agentRun.error()
                            : untouched && "agent_error".equals(failureKind) ? untouchedReason(agentRun)
                            : untouched ? "workspace unchanged after the agent ran; " + verdict.reasoning()
                            : verdict.reasoning();
                    results.add(new RunRecord(spec.name(), spec.model(), eval.id(), eval.project(), attempt,
                            verdict.pass(), agentRun.durationMs(), agentRun.costUsd(), ws.toString(),
                            Instant.now().toString(), UUID.randomUUID().toString(), spec.provider(), "agent",
                            evalHash, agentHash, benchmarkVersion, failureKind, failureReason,
                            recordedJavaVersion, System.getProperty("os.name"),
                            System.getProperty("os.arch"), cliVersion, "provider-default", attempts,
                            agentRun.inputTokens(), agentRun.outputTokens(), agentRun.totalTokens(),
                            candidateHash, agentRun.responseText(), campaignId));
                    resultStore.save(results);
                    System.out.println(verdict.pass() ? "✓ PASSED" : "✗ failed");
                    if (verdict.pass()) {
                        break;
                    }
                }
            }
        }
        reports.print(results);
    }

    private record AgentRun(Long durationMs, Double costUsd, String error,
            Long inputTokens, Long outputTokens, Long totalTokens, String responseText) {
    }

    /**
     * Runs the CLI headless in the container (the SDK adapters would spawn host
     * processes); only claude reports cost and tokens headlessly, others record null.
     */
    private AgentRun runAgentInContainer(DockerSandbox.Container container, AgentSpec spec, EvalDefinition eval) {
        String prompt;
        try {
            prompt = Files.readString(eval.promptFile());
        } catch (Exception e) {
            throw new IllegalStateException("could not read " + eval.promptFile(), e);
        }
        try {
            DockerSandbox.ExecResult result = container.exec(
                    DockerSandbox.agentCommand(spec.provider(), spec.model(), prompt), eval.agentTimeout());
            String error = result.timedOut()
                    ? "agent timed out after " + eval.agentTimeout().toSeconds() + "s"
                    : result.exitCode() != 0 ? "agent CLI exited " + result.exitCode() : null;
            DockerSandbox.ClaudeHeadlessResult parsed = "claude".equals(spec.provider())
                    ? DockerSandbox.parseClaudeJson(result.output())
                    : null;
            if (parsed == null) {
                return new AgentRun(result.durationMs(), null, error, null, null, null, truncate(result.output()));
            }
            Long total = parsed.inputTokens() == null || parsed.outputTokens() == null ? null
                    : parsed.inputTokens() + parsed.outputTokens();
            return new AgentRun(result.durationMs(), parsed.costUsd(), error, parsed.inputTokens(),
                    parsed.outputTokens(), total,
                    truncate(parsed.resultText() != null ? parsed.resultText() : result.output()));
        } catch (RuntimeException e) {
            System.out.println("agent execution failed: " + e.getMessage());
            return new AgentRun(null, null, e.getClass().getSimpleName() + ": " + e.getMessage(),
                    null, null, null, null);
        }
    }

    private AgentRun runAgent(AgentSpec spec, EvalDefinition eval, Path ws) {
        String prompt;
        try {
            prompt = Files.readString(eval.promptFile());
        } catch (Exception e) {
            throw new IllegalStateException("could not read " + eval.promptFile(), e);
        }

        long started = System.currentTimeMillis();
        // Env must go through the process environment (SDK option env is a
        // dead store in agent-claude 0.16.0); restored in finally.
        Agents.EnvPlan plan = agents.envPlan(spec);
        EnvSandbox.Scope envScope = EnvSandbox.apply(plan.overrides(), plan.removals());
        AgentModel model = null;
        try {
            model = agents.createModel(spec, eval.agentTimeout(), plan);
            AgentClientResponse response = AgentClient.create(model)
                    .goal(prompt)
                    .workingDirectory(ws)
                    .run();

            Long durationMs = response.getMetadata() != null && response.getMetadata().getDuration() != null
                    ? response.getMetadata().getDuration().toMillis()
                    : System.currentTimeMillis() - started;
            return new AgentRun(durationMs, extractCost(response), null,
                    extractToken(response, "inputTokens", "input_tokens", "promptTokens", "prompt_tokens"),
                    extractToken(response, "outputTokens", "output_tokens", "completionTokens", "completion_tokens"),
                    extractToken(response, "totalTokens", "total_tokens"), truncate(response.getResult()));
        } catch (Exception e) {
            System.out.println("agent execution failed: " + e.getMessage());
            return new AgentRun(System.currentTimeMillis() - started, null,
                    e.getClass().getSimpleName() + ": " + e.getMessage(), null, null, null, null);
        } finally {
            if (model instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception ignored) {
                }
            }
            EnvSandbox.restore(envScope);
        }
    }

    private static Double extractCost(AgentClientResponse response) {
        if (response.getMetadata() == null) {
            return null;
        }
        Object cost = response.getMetadata().getProviderFields() != null
                ? response.getMetadata().getProviderFields().get("totalCostUsd")
                : null;
        if (cost == null) {
            cost = response.getMetadata().getOrDefault("totalCostUsd", null);
        }
        return cost instanceof Number number ? number.doubleValue() : null;
    }

    private static Long extractToken(AgentClientResponse response, String... keys) {
        if (response.getMetadata() == null) {
            return null;
        }
        Number found = findNumber(response.getMetadata().getProviderFields(), List.of(keys));
        if (found == null) {
            found = findNumber(response.getMetadata(), List.of(keys));
        }
        return found == null ? null : found.longValue();
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= 10_000) {
            return value;
        }
        return value.substring(0, 10_000) + "\n[truncated]";
    }

    private static Number findNumber(Object value, List<String> keys) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        for (String key : keys) {
            Object direct = map.get(key);
            if (direct instanceof Number number) {
                return number;
            }
        }
        for (Object nested : map.values()) {
            Number found = findNumber(nested, keys);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private List<EvalDefinition> selectTargets(Map<String, String> opts) {
        List<EvalDefinition> targets = opts.containsKey("eval")
                ? List.of(catalog.load(opts.get("eval")))
                : catalog.all().stream()
                        .filter(e -> !opts.containsKey("project") || e.project().equals(opts.get("project")))
                        .filter(e -> !opts.containsKey("difficulty")
                                || List.of(opts.get("difficulty").split(",")).contains(e.meta().get("difficulty")))
                        .filter(e -> !opts.containsKey("pilot") || Boolean.parseBoolean(e.meta().getOrDefault("pilot", "false")))
                        .toList();
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("no evals matched");
        }
        return targets;
    }

    private static final List<String> RUN_ADJECTIVES = List.of(
            "eager", "lazy", "swift", "calm", "bold", "keen", "brisk", "quiet", "lively", "steady",
            "bright", "merry", "nimble", "sturdy", "gentle", "daring");

    private static final List<String> RUN_NOUNS = List.of(
            "bean", "boot", "batch", "flux", "mono", "cache", "proxy", "filter", "servlet", "actuator",
            "starter", "context", "advisor", "binder", "reactor", "webhook");

    /** Run names are the campaign identity in results, so collisions get a numeric suffix. */
    private static String resolveRunName(String requested, List<RunRecord> existing) {
        java.util.Set<String> taken = new java.util.HashSet<>();
        for (RunRecord record : existing) {
            if (record.campaignId() != null) {
                taken.add(record.campaignId());
            }
        }
        String base;
        if (requested != null && !requested.isBlank()) {
            base = requested.strip().toLowerCase().replaceAll("[^a-z0-9-]+", "-");
        } else {
            java.util.Random random = new java.util.Random();
            base = RUN_ADJECTIVES.get(random.nextInt(RUN_ADJECTIVES.size())) + "-"
                    + RUN_NOUNS.get(random.nextInt(RUN_NOUNS.size())) + "-"
                    + "%02d".formatted(random.nextInt(100));
        }
        String name = base;
        int suffix = 2;
        while (taken.contains(name)) {
            name = base + "-" + suffix++;
        }
        return name;
    }

    private String benchmarkVersion() {
        return ContentHashes.benchmark(root);
    }

    private static String failureKind(AgentRun agentRun, Judgment verdict, Path workspace, boolean untouched) {
        if (agentRun.error() != null) {
            return "agent_error";
        }
        // A CLI can fail cleanly (exit 0) and score a fake 0%. An untouched workspace alone
        // is not proof, so reclassify only when the run was also too fast to have engaged.
        if (untouched && agentRun.durationMs() != null && agentRun.durationMs() < 20_000) {
            return "agent_error";
        }
        if (verdict.error() != null) {
            return "judge_error";
        }
        String text = verdict.reasoning() == null ? "" : verdict.reasoning();
        try {
            Path log = workspace.resolve("maven-output.log");
            if (Files.exists(log)) {
                text += "\n" + Files.readString(log);
            }
        } catch (Exception ignored) {
        }
        if (text.contains("required modern Spring mechanism") || text.contains("forbidden workaround")
                || text.contains("suppress or redirect hidden tests")) {
            return "policy_failure";
        }
        if (text.contains("COMPILATION ERROR") || text.contains("Compilation failure")) {
            return "compile_failure";
        }
        return "test_failure";
    }

    private static String untouchedReason(AgentRun agentRun) {
        String said = agentRun.responseText() == null ? "(no response)"
                : agentRun.responseText().length() > 300
                        ? agentRun.responseText().substring(0, 300) + "…"
                        : agentRun.responseText();
        return "agent made no changes to the workspace; the CLI likely failed without a nonzero exit. Agent said: "
                + said;
    }

    private static String detectCliVersion(String provider) {
        String command = switch (provider) {
            case "claude" -> "claude";
            case "codex" -> "codex";
            case "gemini" -> "gemini";
            case "qwen-code" -> "qwen";
            default -> provider;
        };
        try {
            Process process = new ProcessBuilder(command, "--version").redirectErrorStream(true).start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "timeout";
            }
            String output = new String(process.getInputStream().readAllBytes()).trim();
            return output.isBlank() ? "unknown" : output.lines().findFirst().orElse("unknown");
        } catch (Exception e) {
            return "unavailable";
        }
    }

    private static Path findRepoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("evals")) && Files.isDirectory(dir.resolve("agents"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("run from inside the spring-evals repository");
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String key = args[i].substring(2);
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    opts.put(key, args[++i]);
                } else {
                    opts.put(key, "true");
                }
            }
        }
        return opts;
    }

    private static List<String> parsePositionals(String[] args) {
        List<String> positionals = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    i++;
                }
            } else {
                positionals.add(args[i]);
            }
        }
        return positionals;
    }
}
