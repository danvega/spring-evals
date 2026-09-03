package dev.danvega.springevals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.danvega.springevals.Agents.AgentSpec;
import dev.danvega.springevals.ResultStore.RunRecord;
import dev.danvega.springevals.cli.AgentCli;
import dev.danvega.springevals.cli.Transcript;

/** Entry point behind ./spring-evals; every agent and every judge runs in a fresh container. */
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
        // Exit explicitly so a lingering non-daemon thread can never hang the wrapper.
        System.exit(0);
    }

    /** Every sample runs, so the projection is evals times samples times the per-sample estimate. */
    void estimate(Map<String, String> opts) {
        int samples = resolveSamples(opts);
        List<EvalDefinition> evals = selectTargets(opts);
        // No selector mirrors run --all-agents (local selection applied), so the projection matches a run.
        SelectionConfig selection = SelectionConfig.load(root, agents.names());
        List<AgentSpec> specs = opts.containsKey("agent") || opts.containsKey("family") || opts.containsKey("all-agents")
                ? resolveAgents(opts)
                : agents.loadAll().stream().filter(spec -> selection.enabled(spec.name())).toList();

        double total = 0;
        System.out.printf("%n%-24s %16s %10s %14s%n", "agent", "$/sample (est)", "samples", "projected");
        for (AgentSpec spec : specs) {
            if (spec.estCostPerAttemptUsd() == null) {
                System.out.printf("%-24s %16s %10d %14s%n", spec.name(), "n/a", evals.size() * samples, "n/a");
                continue;
            }
            double perSample = spec.estCostPerAttemptUsd();
            double projected = evals.size() * samples * perSample;
            total += projected;
            System.out.printf("%-24s %16s %10d %14s%n", spec.name(),
                    "$%.2f".formatted(perSample), evals.size() * samples, "$%.2f".formatted(projected));
        }
        System.out.printf("%-24s %16s %10s %14s%n", "TOTAL", "", "", "$%.2f".formatted(total));
        System.out.printf("%n%d evals x %d samples each. Estimates come from estCostPerAttemptUsd in agents/*.json.%n",
                evals.size(), samples);
        System.out.println("Actual spend is recorded per sample in results/results.json where the CLI reports it.");
    }

    /** Samples per (agent, eval) cell; every sample runs, so this is the multiplier on spend. */
    static int resolveSamples(Map<String, String> opts) {
        if (opts.containsKey("attempts")) {
            throw new IllegalArgumentException("--attempts was replaced by --samples <n>: every sample runs "
                    + "and the pass rate is passes over samples");
        }
        String requested = opts.getOrDefault("samples", "3");
        int value;
        try {
            value = Integer.parseInt(requested);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--samples takes an integer between 1 and 10");
        }
        if (value < 1 || value > 10) {
            throw new IllegalArgumentException("--samples must be between 1 and 10");
        }
        return value;
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
        // The full probe is opt-in: a first-use image build takes minutes a plain doctor call must not trigger.
        boolean probe = opts.containsKey("docker");
        boolean dockerCheckFailed = false;
        boolean json = opts.containsKey("json");
        if (probe) {
            List<EvalDefinition> all = catalog.all();
            dockerCheckFailed = !DockerSandbox.selfCheck(root, all.isEmpty() ? null : all.get(0).projectDir());
            System.out.println();
        } else if (!json) {
            // --json keeps stdout a single JSON document; the docker status is inside it.
            printDockerStatus();
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
        SelectionConfig selection = SelectionConfig.load(root, agents.names());
        java.util.Set<String> excluded = specs.stream().map(AgentSpec::name)
                .filter(name -> !selection.enabled(name))
                .collect(java.util.stream.Collectors.toSet());
        int result;
        if (json) {
            new AgentDoctor().printJson(specs, excluded);
            result = 0;
        } else {
            result = specs.isEmpty() ? 0 : new AgentDoctor().print(specs, excluded);
        }
        if (invalid > 0) {
            System.out.println("Additionally, " + invalid + " agent config file(s) could not be parsed.");
        }
        return dockerCheckFailed || invalid > 0 ? 1 : result;
    }

    private void printDockerStatus() {
        if (!DockerSandbox.dockerAvailable()) {
            System.out.println("✗ docker daemon not reachable; every run and validate needs it\n");
            return;
        }
        String image = DockerSandbox.imageTag(root);
        System.out.println("✓ docker daemon reachable");
        System.out.println(DockerSandbox.imageExists(image)
                ? "✓ benchmark image present: " + image
                : "! benchmark image not built yet (" + image + "); the first run or validate builds it, "
                        + "or probe everything now with doctor --docker");
        System.out.println();
    }

    boolean validate(List<String> ids, Map<String, String> opts) {
        DockerSandbox.requireDocker();
        DockerSandbox.pruneStaleContainers();
        String image = DockerSandbox.ensureImage(root);
        System.out.println(sandboxBanner(image));
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

            List<EvalDefinition.Candidate> candidates = eval.candidates();
            int gates = 1 + candidates.size();
            System.out.println("1/" + gates + " broken project must FAIL the hidden tests...");
            Path brokenWs = workspaces.freshCopy(eval, "validate-broken");
            workspaces.injectEvalTests(eval, brokenWs);
            Judgment broken = judgeWorkspace(image, eval, brokenWs, true);
            if (broken.testsPassed() != null && broken.testsPassed()) {
                ok = false;
                System.out.println("   ✗ broken project unexpectedly passed the hidden tests (" + brokenWs + ")");
            } else {
                String actualFailure = broken.outcome().recordValue();
                String expectedFailure = eval.meta().get("baseline_failure");
                if (!expectedFailure.equals(actualFailure)) {
                    ok = false;
                    System.out.println("   ✗ baseline failed for the wrong reason: expected " + expectedFailure
                            + ", got " + actualFailure + ": " + broken.reasoning());
                } else {
                    System.out.println("   ✓ fails as expected (" + actualFailure + ")");
                }
            }

            int gate = 2;
            for (EvalDefinition.Candidate candidate : candidates) {
                String expected = candidate.expected().recordValue();
                System.out.println(gate++ + "/" + gates + " " + candidate.label() + " must reach " + expected + "...");
                Path candidateWs = workspaces.freshCopy(eval, "validate-" + candidate.label().toLowerCase()
                        .replaceAll("[^a-z0-9]+", "-"));
                workspaces.applyCandidate(candidate.dir(), candidateWs);
                workspaces.injectEvalTests(eval, candidateWs);
                Judgment verdict = judgeWorkspace(image, eval, candidateWs, false);
                if (verdict.outcome() != candidate.expected()) {
                    ok = false;
                    System.out.println("   ✗ " + candidate.label() + " reached " + verdict.outcome().recordValue()
                            + ": " + verdict.reasoning());
                    System.out.println("     see " + candidateWs.resolve("maven-output.log"));
                } else {
                    System.out.println("   ✓ " + expected + (verdict.pass() ? "" : " (" + verdict.reasoning() + ")"));
                }
            }
        }

        System.out.println(ok ? "\nAll validations passed." : "\nValidation FAILED.");
        return ok;
    }

    private static String sandboxBanner(String image) {
        return "Sandbox: agent and judge run in fresh per-sample containers from " + image;
    }

    /** Judges one workspace in a fresh, env-free judge container. */
    private Judgment judgeWorkspace(String image, EvalDefinition eval, Path ws, boolean behaviorOnly) {
        try (DockerSandbox.Container container = DockerSandbox.start(ws, Map.of(), image)) {
            return behaviorOnly
                    ? mavenJudge.judgeBehaviorOnly(eval, ws, container.buildRunner())
                    : mavenJudge.judge(eval, ws, container.buildRunner());
        }
    }

    /** --family matches the agent-name prefix; explicit --agent picks bypass local selection. */
    private List<AgentSpec> resolveAgents(Map<String, String> opts) {
        SelectionConfig selection = SelectionConfig.load(root, agents.names());
        if (opts.containsKey("agent")) {
            List<AgentSpec> picked = List.of(opts.get("agent").split(",")).stream().map(agents::load).toList();
            picked.stream().filter(spec -> !selection.enabled(spec.name())).forEach(spec -> System.out.printf(
                    "note: %s is not in enabledAgents in %s; running it anyway because you named it%n",
                    spec.name(), SelectionConfig.FILE_NAME));
            return picked;
        }
        if (opts.containsKey("family")) {
            List<AgentSpec> family = agents.loadAll().stream()
                    .filter(spec -> selection.enabled(spec.name()))
                    .filter(spec -> spec.name().startsWith(opts.get("family")))
                    .toList();
            if (family.isEmpty()) {
                throw new IllegalArgumentException("no enabled agents match family '" + opts.get("family") + "'");
            }
            return family;
        }
        if (opts.containsKey("all-agents")) {
            List<AgentSpec> all = agents.loadAll();
            List<AgentSpec> enabled = all.stream().filter(spec -> selection.enabled(spec.name())).toList();
            long excluded = all.size() - enabled.size();
            if (excluded > 0) {
                System.out.printf("skipping %d agent(s) not in enabledAgents in %s%n",
                        excluded, SelectionConfig.FILE_NAME);
            }
            return enabled;
        }
        throw new IllegalArgumentException(
                "pick agents with --agent <name[,name...]>, --family <prefix>, or --all-agents");
    }

    void run(Map<String, String> opts) throws Exception {
        DockerSandbox.requireDocker();
        int parallel = resolveParallel(opts.get("parallel"));
        List<AgentSpec> specs = resolveAgents(opts);
        int samples = resolveSamples(opts);
        boolean force = opts.containsKey("force");

        List<EvalDefinition> targets = selectTargets(opts);
        double projectedMaximum = specs.stream()
                .mapToDouble(spec -> spec.estCostPerAttemptUsd() == null ? 0 : spec.estCostPerAttemptUsd())
                .sum() * targets.size() * samples;
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

        // The image must exist before any money is spent.
        DockerSandbox.pruneStaleContainers();
        String image = DockerSandbox.ensureImage(root);
        System.out.println(sandboxBanner(image));
        String recordedJavaVersion = DockerSandbox.javaVersion(image);

        List<RunRecord> loaded = resultStore.load();
        String campaignId = resolveRunName(opts.get("run-name"), loaded);
        System.out.println("Run name: " + campaignId);

        RunContext ctx = new RunContext(image, toolchain(image), targets, samples, force, paid, benchmarkVersion(),
                recordedJavaVersion, campaignId, new RunScheduler.CostReserver(costCap),
                new RunScheduler.ResultsLedger(loaded, resultStore::save),
                new java.util.concurrent.ConcurrentHashMap<>(),
                new java.util.concurrent.atomic.AtomicBoolean(false));

        var lanes = RunScheduler.partitionByLane(specs);
        if (parallel > 1 && lanes.size() > 1) {
            System.out.println("Lanes in parallel: " + String.join(", ", lanes.keySet())
                    + " (samples within a lane stay serial; max " + parallel + " concurrent containers)");
            RunScheduler.runLanes(lanes, parallel,
                    (lane, laneSpecs) -> runLane(ctx, laneSpecs, prefixedLog(lane)));
        } else {
            runLane(ctx, specs, System.out::println);
        }
        reports.print(ctx.ledger().snapshot());
    }

    static int resolveParallel(String requested) {
        if (requested == null) {
            return 4;
        }
        int value;
        try {
            value = Integer.parseInt(requested);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--parallel takes an integer between 1 and 8");
        }
        if (value < 1 || value > 8) {
            throw new IllegalArgumentException("--parallel must be between 1 and 8");
        }
        return value;
    }

    /** Whole messages print as one contiguous block so lane output never interleaves mid-message. */
    private static java.util.function.Consumer<String> prefixedLog(String lane) {
        return text -> {
            StringBuilder block = new StringBuilder();
            for (String line : text.split("\n", -1)) {
                block.append('[').append(lane).append("] ").append(line).append(System.lineSeparator());
            }
            System.out.print(block);
        };
    }

    private record RunContext(String image, String toolchain, List<EvalDefinition> targets, int samples,
            boolean force, boolean paid, String benchmarkVersion, String recordedJavaVersion, String campaignId,
            RunScheduler.CostReserver reserver, RunScheduler.ResultsLedger ledger,
            Map<String, String> cliVersions, java.util.concurrent.atomic.AtomicBoolean stopped) {
    }

    /** One lane never runs more than one container at a time (per-account rate limits). */
    private void runLane(RunContext ctx, List<AgentSpec> laneSpecs, java.util.function.Consumer<String> log) {
        for (AgentSpec spec : laneSpecs) {
            AgentCli cli = AgentCli.forProvider(spec.provider());
            String cliVersion = ctx.cliVersions().computeIfAbsent(cli.id(),
                    ignored -> DockerSandbox.cliVersion(cli, ctx.image()));
            for (EvalDefinition eval : ctx.targets()) {
                if (ctx.stopped().get()) {
                    return;
                }
                runAgentEval(ctx, cli, spec, eval, cliVersion, log);
            }
        }
    }

    private void runAgentEval(RunContext ctx, AgentCli cli, AgentSpec spec, EvalDefinition eval, String cliVersion,
            java.util.function.Consumer<String> log) {
        String evalHash = ContentHashes.eval(eval);
        String agentHash = ContentHashes.agent(root, spec.name());
        String osName = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");
        java.util.function.Predicate<RunRecord> identity = r -> r.agent().equals(spec.name())
                && r.eval().equals(eval.id())
                && evalHash.equals(r.evalHash()) && agentHash.equals(r.agentConfigHash())
                && ctx.benchmarkVersion().equals(r.benchmarkVersion())
                && cliVersion.equals(r.cliVersion())
                && ctx.recordedJavaVersion().equals(r.javaVersion())
                && osName.equals(r.osName()) && osArch.equals(r.osArch());

        List<RunRecord> existing = ctx.ledger().matching(identity);
        if (ctx.force()) {
            ctx.ledger().removeMatching(identity);
            existing = List.of();
        }
        int toRun = samplesToRun(existing, ctx.samples());
        if (toRun == 0) {
            log.accept("skip %s (%s): already holds %d verdict sample(s) under this identity; use --force to rerun"
                    .formatted(eval.id(), spec.name(), ctx.samples()));
            return;
        }

        Path hostHome = Path.of(System.getProperty("user.home"));
        int first = nextSampleNumber(existing);
        // Every sample runs; an earlier pass never short-circuits the cell, and
        // infrastructure failures do not fill it.
        for (int sample = first; sample < first + toRun; sample++) {
            if (ctx.stopped().get()) {
                return;
            }
            double reservation = spec.estCostPerAttemptUsd() == null
                    ? Double.POSITIVE_INFINITY : spec.estCostPerAttemptUsd();
            if (ctx.paid() && !ctx.reserver().reserve(reservation)) {
                log.accept("Cost cap reached before %s / %s sample %d. Reserved $%.2f of $%.2f.".formatted(
                        eval.id(), spec.name(), sample, ctx.reserver().reserved(), ctx.reserver().cap()));
                ctx.stopped().set(true);
                return;
            }
            log.accept("%n=== %s / %s / sample %d (cell target %d verdict samples) ===".formatted(eval.id(),
                    spec.name(), sample, ctx.samples()));
            Path ws = workspaces.freshCopy(eval, spec.name() + "-s" + sample);
            String baselineHash = ContentHashes.candidate(ws);

            AgentSession session;
            // The agent container is destroyed before hidden tests are injected, so
            // nothing it left running or wrote outside the workspace touches judging.
            try (DockerSandbox.Container agentContainer = DockerSandbox.start(ws,
                    Agents.expandAll(spec.env()), ctx.image())) {
                for (AgentCli.SeedFile seed : cli.seedFiles(spec, hostHome)) {
                    agentContainer.seed(seed);
                }
                log.accept("running agent CLI in container " + agentContainer.name() + "...");
                session = runAgentInContainer(agentContainer, cli, spec, eval, log);
            }
            // Stored, summarized, and scanned only once the container is gone; the raw stream never enters results.
            AgentRun agentRun = captureTranscript(session, cli, spec, eval, ctx.campaignId(), sample, log);
            String candidateHash = ContentHashes.candidate(ws);
            boolean untouched = baselineHash.equals(candidateHash);
            log.accept("injecting hidden tests, judging with ./mvnw clean test in a fresh container...");
            workspaces.injectEvalTests(eval, ws);
            Judgment verdict;
            try (DockerSandbox.Container judgeContainer = DockerSandbox.start(ws, Map.of(), ctx.image())) {
                verdict = mavenJudge.judge(eval, ws, judgeContainer.buildRunner());
            }
            if (ctx.paid() && agentRun.costUsd() != null && agentRun.costUsd() > reservation) {
                ctx.reserver().absorbOverrun(agentRun.costUsd() - reservation);
            }

            String failureKind = failureKind(agentRun, verdict, untouched);
            boolean agentError = "agent_error".equals(failureKind);
            String outcome = agentError ? "agent_error" : verdict.outcome().recordValue();
            String failureReason = failureKind == null ? null
                    : agentError && agentRun.error() != null ? agentRun.error()
                    : agentError ? untouchedReason(agentRun)
                    : untouched ? "workspace unchanged after the agent ran; " + verdict.reasoning()
                    : verdict.reasoning();
            ctx.ledger().append(new RunRecord(spec.name(), spec.model(), eval.id(), eval.project(), sample,
                    verdict.pass() && !agentError, agentRun.durationMs(), agentRun.costUsd(), ws.toString(),
                    Instant.now().toString(), UUID.randomUUID().toString(), spec.provider(), "agent",
                    evalHash, agentHash, ctx.benchmarkVersion(), failureKind, failureReason,
                    ctx.recordedJavaVersion(), osName, osArch, cliVersion, "provider-default", ctx.samples(),
                    agentRun.inputTokens(), agentRun.outputTokens(), agentRun.totalTokens(),
                    candidateHash, agentRun.responseText(), ctx.campaignId(),
                    outcome, agentError ? null : verdict.testsPassed(), agentError ? null : verdict.idiomatic(),
                    agentRun.exitCode(), agentRun.timedOut(),
                    ctx.toolchain(), agentRun.transcriptPath(), agentRun.transcript(), agentRun.contaminationFlags()));
            log.accept(switch (outcome) {
                case "pass" -> "✓ pass";
                case "functional_only" -> "~ functional_only: " + verdict.reasoning();
                default -> "✗ " + outcome;
            });
        }
    }

    record AgentRun(Long durationMs, Double costUsd, String error,
            Long inputTokens, Long outputTokens, Long totalTokens, String responseText,
            Integer exitCode, Boolean timedOut, String transcriptPath, Transcript transcript,
            List<String> contaminationFlags) {
    }

    /** What the container returned: the parsed run plus the raw stream, which lives only until it is stored. */
    record AgentSession(AgentRun run, String rawOutput) {
    }

    /** Image tag plus every CLI pin: the toolchain a row ran on, recorded, never part of identity. */
    static String toolchain(String image) {
        return image + "; " + AgentCli.all().stream()
                .map(cli -> cli.npmPackage() + "@" + cli.pinnedVersion())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    /** Only verdict samples fill a cell; a rerun tops it up by appending, never by replacing. */
    static int samplesToRun(List<RunRecord> existing, int samples) {
        long verdicts = existing.stream().filter(RunRecord::isVerdict).count();
        return (int) Math.max(0, samples - verdicts);
    }

    /** Sample numbers stay unique within a cell even when infrastructure failures are on record. */
    static int nextSampleNumber(List<RunRecord> existing) {
        return existing.stream().mapToInt(RunRecord::sample).max().orElse(0) + 1;
    }

    /** Headless CLI run inside the agent container; only claude reports cost and tokens, others record null. */
    private AgentSession runAgentInContainer(DockerSandbox.Container container, AgentCli cli, AgentSpec spec,
            EvalDefinition eval, java.util.function.Consumer<String> log) {
        String prompt;
        try {
            prompt = Files.readString(eval.promptFile());
        } catch (Exception e) {
            throw new IllegalStateException("could not read " + eval.promptFile(), e);
        }
        try {
            DockerSandbox.ExecResult result = container.exec(
                    cli.headlessCommand(prompt, spec.model()), eval.agentTimeout());
            String error = result.timedOut()
                    ? "agent timed out after " + eval.agentTimeout().toSeconds() + "s"
                    : result.exitCode() != 0 ? "agent CLI exited " + result.exitCode() : null;
            AgentCli.AgentOutput parsed = cli.parse(result.output(), result.exitCode());
            Long total = parsed.inputTokens() == null || parsed.outputTokens() == null ? null
                    : parsed.inputTokens() + parsed.outputTokens();
            return new AgentSession(new AgentRun(result.durationMs(), parsed.costUsd(), error, parsed.inputTokens(),
                    parsed.outputTokens(), total, truncate(parsed.responseText()), result.exitCode(),
                    result.timedOut(), null, null, List.of()), result.output());
        } catch (RuntimeException e) {
            log.accept("agent execution failed: " + e.getMessage());
            return new AgentSession(new AgentRun(null, null, e.getClass().getSimpleName() + ": " + e.getMessage(),
                    null, null, null, null, null, null, null, null, List.of()), null);
        }
    }

    private AgentRun captureTranscript(AgentSession session, AgentCli cli, AgentSpec spec, EvalDefinition eval,
            String campaignId, int sample, java.util.function.Consumer<String> log) {
        AgentRun run = session.run();
        String output = session.rawOutput();
        String transcriptPath = storeTranscript(cli, spec, eval, campaignId, sample, output, log);
        Transcript transcript = output == null ? null : cli.summarize(output);
        List<String> flags = Contamination.scan(output, eval);
        if (!flags.isEmpty()) {
            log.accept("! contamination flags: " + String.join("; ", flags));
        }
        String response = run.responseText() != null ? run.responseText()
                : output == null ? null
                : "no final response in the stream; transcript at " + (transcriptPath == null ? "(not stored)" : transcriptPath);
        return new AgentRun(run.durationMs(), run.costUsd(), run.error(), run.inputTokens(), run.outputTokens(),
                run.totalTokens(), response, run.exitCode(), run.timedOut(), transcriptPath, transcript, flags);
    }

    /** The raw session stays outside the repository; a failure to store it is logged, never scored. */
    private String storeTranscript(AgentCli cli, AgentSpec spec, EvalDefinition eval, String campaignId, int sample,
            String output, java.util.function.Consumer<String> log) {
        if (output == null) {
            return null;
        }
        Path file = workspaces.transcriptsDir().resolve(campaignId).resolve(spec.name())
                .resolve(eval.id().replace('/', '-') + "-s" + sample + "." + cli.transcriptExtension());
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, output);
            return file.toString();
        } catch (IOException e) {
            log.accept("could not store the transcript at " + file + ": " + e.getMessage());
            return null;
        }
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= 10_000) {
            return value;
        }
        return value.substring(0, 10_000) + "\n[truncated]";
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

    /**
     * The judge decides on the workspace. An agent exit code or timeout is
     * recorded but overrides nothing: agent_error only when the workspace is
     * untouched and either the CLI reported an error or it finished too fast
     * to have engaged (a CLI that fails with exit 0 would otherwise score a fake 0%).
     */
    static String failureKind(AgentRun agentRun, Judgment verdict, boolean untouched) {
        boolean tooFast = agentRun.durationMs() != null && agentRun.durationMs() < 20_000;
        if (untouched && (agentRun.error() != null || tooFast || agentRun.durationMs() == null)) {
            return "agent_error";
        }
        return verdict.failureKind();
    }

    private static String untouchedReason(AgentRun agentRun) {
        String said = agentRun.responseText() == null ? "(no response)"
                : agentRun.responseText().length() > 300
                        ? agentRun.responseText().substring(0, 300) + "…"
                        : agentRun.responseText();
        return "agent made no changes to the workspace; the CLI likely failed without a nonzero exit. Agent said: "
                + said;
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
