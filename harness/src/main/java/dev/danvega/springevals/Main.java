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

/**
 * Spring Evals harness, built on the Spring AI Community Agent Client.
 *
 * Usage:
 *   ./spring-evals list
 *   ./spring-evals validate [evalId...]
 *   ./spring-evals doctor [--agent a[,b,c] | --family claude | --all-agents]
 *   ./spring-evals run --agent a[,b,c] | --family claude | --all-agents
 *                      [--eval boot/000-jackson3-migration] [--project boot] [--difficulty easy,medium]
 *                      [--pilot] [--attempts 1] [--force]
 *                      [--allow-paid-run --max-total-cost USD]
 *   ./spring-evals report
 */
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
            case "validate" -> System.exit(main.validate(positionals) ? 0 : 1);
            case "doctor" -> System.exit(main.doctor(opts));
            case "run" -> main.run(opts);
            case "report" -> main.reports.print(main.resultStore.load());
            case "estimate" -> main.estimate(opts);
            default -> {
                System.out.println("usage: ./spring-evals <list|validate|doctor|run|report|estimate>");
                System.exit(command.isEmpty() ? 0 : 1);
            }
        }
    }

    /**
     * Projects spend before running: expected assumes ~1.7 attempts per eval,
     * worst case assumes every attempt is used.
     */
    void estimate(Map<String, String> opts) {
        int attempts = Integer.parseInt(opts.getOrDefault("attempts", "1"));
        List<EvalDefinition> evals = selectTargets(opts);
        List<AgentSpec> specs = opts.containsKey("agent") || opts.containsKey("family") || opts.containsKey("all-agents")
                ? resolveAgents(opts)
                : agents.loadAll();

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
        return invalid > 0 ? 1 : result;
    }

    boolean validate(List<String> ids) {
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
            Judgment broken = mavenJudge.judgeBehaviorOnly(eval, brokenWs);
            if (broken.pass()) {
                ok = false;
                System.out.println("   ✗ broken project unexpectedly PASSED (" + brokenWs + ")");
            } else {
                String actualFailure = failureKind(new AgentRun(0L, null, null, null, null, null, null), broken, brokenWs);
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
            Judgment solution = mavenJudge.judge(eval, solutionWs);
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

    /**
     * Resolves which agents a command targets: --agent a[,b,c] for explicit
     * picks, --family <prefix> for a model family (matches the agent name
     * prefix, e.g. "claude" or "codex"), or --all-agents for the whole matrix.
     */
    private List<AgentSpec> resolveAgents(Map<String, String> opts) {
        if (opts.containsKey("agent")) {
            return List.of(opts.get("agent").split(",")).stream().map(agents::load).toList();
        }
        if (opts.containsKey("family")) {
            List<AgentSpec> family = agents.loadAll().stream()
                    .filter(spec -> spec.name().startsWith(opts.get("family")))
                    .toList();
            if (family.isEmpty()) {
                throw new IllegalArgumentException("no agents match family '" + opts.get("family") + "'");
            }
            return family;
        }
        if (opts.containsKey("all-agents")) {
            return agents.loadAll();
        }
        throw new IllegalArgumentException(
                "pick agents with --agent <name[,name...]>, --family <prefix>, or --all-agents");
    }

    void run(Map<String, String> opts) throws Exception {
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

        List<RunRecord> results = resultStore.load();
        double reservedCost = 0;
        String benchmarkVersion = benchmarkVersion();
        Map<String, String> cliVersions = new HashMap<>();

        for (AgentSpec spec : specs) {
            String cliVersion = cliVersions.computeIfAbsent(spec.provider(), Main::detectCliVersion);
            for (EvalDefinition eval : targets) {
                String evalHash = ContentHashes.eval(eval);
                String agentHash = ContentHashes.agent(root, spec.name());
                List<RunRecord> existing = results.stream()
                        .filter(r -> r.agent().equals(spec.name()) && r.eval().equals(eval.id()))
                        .filter(r -> evalHash.equals(r.evalHash()) && agentHash.equals(r.agentConfigHash()))
                        .filter(r -> benchmarkVersion.equals(r.benchmarkVersion()))
                        .filter(r -> cliVersion.equals(r.cliVersion()))
                        .filter(r -> System.getProperty("java.version").equals(r.javaVersion()))
                        .filter(r -> System.getProperty("os.name").equals(r.osName()))
                        .filter(r -> System.getProperty("os.arch").equals(r.osArch()))
                        .toList();

                if (force) {
                    results = new ArrayList<>(results.stream()
                            .filter(r -> !(r.agent().equals(spec.name()) && r.eval().equals(eval.id())
                                    && evalHash.equals(r.evalHash()) && agentHash.equals(r.agentConfigHash())
                                    && benchmarkVersion.equals(r.benchmarkVersion())
                                    && cliVersion.equals(r.cliVersion())
                                    && System.getProperty("java.version").equals(r.javaVersion())
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

                    System.out.println("running agent through AgentClient...");
                    AgentRun agentRun = runAgent(spec, eval, ws);
                    String candidateHash = ContentHashes.candidate(ws);
                    if (agentRun.costUsd() != null && agentRun.costUsd() > reservation) {
                        reservedCost += agentRun.costUsd() - reservation;
                    }

                    System.out.println("injecting hidden tests, judging with ./mvnw clean test...");
                    workspaces.injectEvalTests(eval, ws);
                    Judgment verdict = mavenJudge.judge(eval, ws);

                    String failureKind = verdict.pass() ? null : failureKind(agentRun, verdict, ws);
                    String failureReason = verdict.pass() ? null
                            : agentRun.error() != null ? agentRun.error() : verdict.reasoning();
                    results.add(new RunRecord(spec.name(), spec.model(), eval.id(), eval.project(), attempt,
                            verdict.pass(), agentRun.durationMs(), agentRun.costUsd(), ws.toString(),
                            Instant.now().toString(), UUID.randomUUID().toString(), spec.provider(), "agent",
                            evalHash, agentHash, benchmarkVersion, failureKind, failureReason,
                            System.getProperty("java.version"), System.getProperty("os.name"),
                            System.getProperty("os.arch"), cliVersion, "provider-default", attempts,
                            agentRun.inputTokens(), agentRun.outputTokens(), agentRun.totalTokens(),
                            candidateHash, agentRun.responseText()));
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

    private AgentRun runAgent(AgentSpec spec, EvalDefinition eval, Path ws) {
        String prompt;
        try {
            prompt = Files.readString(eval.promptFile());
        } catch (Exception e) {
            throw new IllegalStateException("could not read " + eval.promptFile(), e);
        }

        long started = System.currentTimeMillis();
        AgentModel model = agents.createModel(spec, eval.agentTimeout());
        try {
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

    private String benchmarkVersion() {
        return ContentHashes.benchmark(root);
    }

    private static String failureKind(AgentRun agentRun, Judgment verdict, Path workspace) {
        if (agentRun.error() != null) {
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
