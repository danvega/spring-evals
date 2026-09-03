package dev.danvega.springevals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import dev.danvega.springevals.ResultStore.RunRecord;

/**
 * Coverage-aware benchmark reports. The pass rate is passes over verdict
 * samples; the functional rate counts candidates whose tests passed even
 * when the idiom check did not.
 */
public class Reports {

    private record Row(String agent, String model, int evals, int totalEvals, int samples, boolean eligible,
            double coverage, double passRate, double functionalRate, double ciLower, double ciUpper,
            Long avgTokens, Double avgDurationS, Double avgCostUsd, Double costPerPassUsd, Double totalCostUsd) {
    }

    private record Cell(int passed, int functional, int samples) {
    }

    private final Path repoRoot;
    private final EvalCatalog catalog;
    private final JsonMapper mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    public Reports(Path repoRoot, EvalCatalog catalog) {
        this.repoRoot = repoRoot;
        this.catalog = catalog;
    }

    public void print(List<RunRecord> results) {
        int storedRecords = results.size();
        List<RunRecord> cohort = currentCohort(results);
        if (cohort.isEmpty()) {
            System.out.println("no leaderboard-eligible results in the current cohort");
            if (storedRecords > 0) {
                System.out.println(storedRecords + " stored record(s) belong to an older benchmark cohort; "
                        + "they remain in the dashboard's run history");
            }
            // Run history survives cohort rotation; leaderboard.md must not keep retired rows.
            try {
                Path leaderboard = repoRoot.resolve("results").resolve("leaderboard.md");
                Files.createDirectories(leaderboard.getParent());
                Files.writeString(leaderboard, "# Spring Evals Leaderboard\n\nGenerated " + Instant.now()
                        + "\n\nNo leaderboard-eligible results in the current benchmark cohort. "
                        + "Past runs remain in results/runs/ and the dashboard's run history.\n");
                System.out.println("wrote results/leaderboard.md (empty cohort)");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            writeDashboardData(List.of(), results, List.of(), new LinkedHashSet<>(),
                    catalog.all().stream().map(EvalDefinition::project).distinct().sorted().toList(),
                    0, true, new LinkedHashMap<>());
            writeRunLogs(results);
            return;
        }
        if (storedRecords != cohort.size()) {
            System.out.println("ignored " + (storedRecords - cohort.size())
                    + " stored record(s) from older task, agent-config, or harness identities");
        }

        int totalEvals = catalog.all().size();
        // Infrastructure failures are not verdicts and must never score as 0% for the model.
        List<RunRecord> verdicts = cohort.stream().filter(RunRecord::isVerdict).toList();
        Set<String> agentNames = new LinkedHashSet<>(verdicts.stream().map(RunRecord::agent).toList());
        Map<String, List<RunRecord>> infraOnly = new LinkedHashMap<>();
        for (RunRecord record : cohort) {
            if (!record.isVerdict() && !agentNames.contains(record.agent())) {
                infraOnly.computeIfAbsent(record.agent(), a -> new ArrayList<>()).add(record);
            }
        }
        List<Row> rows = agentNames.stream().map(agent -> rowFor(agent, verdicts, totalEvals))
                .sorted(Comparator.comparing(Row::eligible).reversed()
                        .thenComparing(Comparator.comparingDouble(Row::passRate).reversed())
                        .thenComparing(Comparator.comparingDouble(Row::ciLower).reversed())
                        .thenComparing(Comparator.comparingDouble(Row::functionalRate).reversed())
                        .thenComparing(Row::agent))
                .toList();

        List<Double> knownCosts = cohort.stream().map(RunRecord::costUsd).filter(c -> c != null).toList();
        double knownSpend = knownCosts.stream().mapToDouble(Double::doubleValue).sum();
        boolean spendPartial = knownCosts.size() != cohort.size();

        StringBuilder table = new StringBuilder();
        table.append("| Agent | Model | Coverage | Pass rate | 95% CI | Functional | Samples | Avg Tokens | Avg Time | Avg Cost | Cost / Pass | Total Cost |\n");
        table.append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (Row row : rows) {
            table.append("| %s%s | %s | %d/%d | %s | %s–%s | %s | %d | %s | %s | %s | %s | %s |%n".formatted(
                    row.agent(), row.eligible() ? "" : " (partial)", row.model(), row.evals(), row.totalEvals(),
                    pct(row.passRate()), pct(row.ciLower()), pct(row.ciUpper()), pct(row.functionalRate()),
                    row.samples(), row.avgTokens() == null ? "n/a" : row.avgTokens(),
                    seconds(row.avgDurationS()), usd(row.avgCostUsd()), usd(row.costPerPassUsd()),
                    usd(row.totalCostUsd())));
        }
        double estSpend = estimatedSpend(cohort);
        table.append(("\nRecorded benchmark spend: %s%s. Subscription-billed agents report plan-equivalent "
                + "accounting, not billed dollars.%n").formatted(usd(knownSpend),
                spendPartial ? " (partial: some CLIs did not report cost)" : ""));
        if (spendPartial && estSpend > 0) {
            table.append(("Estimated spend at API prices: %s (samples run x configured per-sample estimates; "
                    + "subscription-billed agents draw on plans instead)%n").formatted(usd(estSpend)));
        }
        table.append("Pass rate is passes over verdict samples. Functional counts samples whose hidden tests passed, "
                + "with or without the idiom. Only full-coverage rows are leaderboard-eligible; partial rows are "
                + "shown for diagnostics.\n");
        if (!infraOnly.isEmpty()) {
            table.append("\nNo verdicts (infrastructure failed before the model saw the task):\n");
            for (var entry : infraOnly.entrySet()) {
                Map<String, Long> kinds = entry.getValue().stream().collect(java.util.stream.Collectors
                        .groupingBy(r -> r.failureKind() == null ? "unknown" : r.failureKind(),
                                java.util.stream.Collectors.counting()));
                table.append("- ").append(entry.getKey()).append(": ").append(kinds).append('\n');
            }
        }

        List<String> projects = catalog.all().stream().map(EvalDefinition::project).distinct().sorted().toList();
        StringBuilder projectTable = projectTable(verdicts, rows, projects);

        System.out.println("\n" + table);
        System.out.println("By project:\n" + projectTable);

        try {
            Path leaderboard = repoRoot.resolve("results").resolve("leaderboard.md");
            Files.createDirectories(leaderboard.getParent());
            Files.writeString(leaderboard, "# Spring Evals Leaderboard\n\nGenerated " + Instant.now()
                    + "\n\n" + table + "\n## By project\n\n" + projectTable);
            System.out.println("wrote results/leaderboard.md");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        writeDashboardData(verdicts, results, rows, agentNames, projects, knownSpend, spendPartial, infraOnly);
        writeRunLogs(results);
    }

    private List<RunRecord> currentCohort(List<RunRecord> records) {
        String benchmark = ContentHashes.benchmark(repoRoot);
        Map<String, String> evalHashes = new LinkedHashMap<>();
        for (EvalDefinition eval : catalog.all()) {
            evalHashes.put(eval.id(), ContentHashes.eval(eval));
        }
        List<RunRecord> contentCurrent = records.stream()
                .filter(r -> benchmark.equals(r.benchmarkVersion()))
                .filter(r -> evalHashes.containsKey(r.eval()) && evalHashes.get(r.eval()).equals(r.evalHash()))
                .filter(r -> {
                    Path config = repoRoot.resolve("agents").resolve(r.agent() + ".json");
                    return Files.exists(config)
                            && ContentHashes.agent(repoRoot, r.agent()).equals(r.agentConfigHash());
                }).toList();
        Map<String, EnvironmentKey> latestEnvironment = new LinkedHashMap<>();
        Map<String, String> latestTimestamp = new LinkedHashMap<>();
        for (RunRecord record : contentCurrent) {
            String timestamp = record.timestamp() == null ? "" : record.timestamp();
            if (!latestTimestamp.containsKey(record.agent())
                    || timestamp.compareTo(latestTimestamp.get(record.agent())) > 0) {
                latestTimestamp.put(record.agent(), timestamp);
                latestEnvironment.put(record.agent(), EnvironmentKey.from(record));
            }
        }
        return contentCurrent.stream()
                .filter(r -> EnvironmentKey.from(r).equals(latestEnvironment.get(r.agent())))
                .toList();
    }

    private record EnvironmentKey(String cliVersion, String javaVersion, String osName, String osArch,
            String track, String networkPolicy) {
        static EnvironmentKey from(RunRecord record) {
            return new EnvironmentKey(record.cliVersion(), record.javaVersion(), record.osName(), record.osArch(),
                    record.track(), record.networkPolicy());
        }
    }

    private Row rowFor(String agent, List<RunRecord> verdicts, int totalEvals) {
        List<RunRecord> mine = verdicts.stream().filter(r -> r.agent().equals(agent)).toList();
        Set<String> evalIds = new LinkedHashSet<>(mine.stream().map(RunRecord::eval).toList());
        Cell all = cell(mine);
        long durationMs = mine.stream().map(RunRecord::agentDurationMs).filter(d -> d != null)
                .mapToLong(Long::longValue).sum();
        List<Double> costs = mine.stream().map(RunRecord::costUsd).filter(c -> c != null).toList();
        boolean completeCost = costs.size() == mine.size();
        Double totalCost = completeCost ? costs.stream().mapToDouble(Double::doubleValue).sum() : null;
        List<Long> tokens = mine.stream().map(Reports::totalTokens).filter(t -> t != null).toList();
        boolean completeTokens = tokens.size() == mine.size();
        double[] interval = wilson(all.passed(), all.samples());
        return new Row(agent, mine.getLast().model(), evalIds.size(), totalEvals, all.samples(),
                evalIds.size() == totalEvals, (double) evalIds.size() / totalEvals,
                rate(all.passed(), all.samples()), rate(all.functional(), all.samples()),
                interval[0], interval[1],
                !completeTokens || mine.isEmpty() ? null
                        : Math.round((double) tokens.stream().mapToLong(Long::longValue).sum() / mine.size()),
                mine.isEmpty() ? null : durationMs / 1000.0 / mine.size(),
                totalCost == null || mine.isEmpty() ? null : totalCost / mine.size(),
                totalCost == null || all.passed() == 0 ? null : totalCost / all.passed(), totalCost);
    }

    private static Cell cell(List<RunRecord> records) {
        int passed = (int) records.stream().filter(RunRecord::passed).count();
        int functional = (int) records.stream().filter(RunRecord::functional).count();
        return new Cell(passed, functional, records.size());
    }

    private static double rate(int count, int samples) {
        return samples == 0 ? 0 : (double) count / samples;
    }

    private StringBuilder projectTable(List<RunRecord> verdicts, List<Row> rows, List<String> projects) {
        StringBuilder table = new StringBuilder();
        table.append("| Agent | ").append(String.join(" | ", projects.stream().map(p -> "spring-" + p).toList()))
                .append(" |\n");
        table.append("|---|").append("---|".repeat(projects.size())).append('\n');
        for (Row row : rows) {
            StringBuilder line = new StringBuilder("| " + row.agent() + " |");
            for (String project : projects) {
                List<String> allIds = catalog.all().stream().filter(e -> e.project().equals(project))
                        .map(EvalDefinition::id).toList();
                List<RunRecord> mine = verdicts.stream().filter(r -> r.agent().equals(row.agent()))
                        .filter(r -> allIds.contains(r.eval())).toList();
                Set<String> attempted = new LinkedHashSet<>(mine.stream().map(RunRecord::eval).toList());
                Cell cell = cell(mine);
                line.append(" %d/%d evals, %d/%d samples passed |".formatted(attempted.size(), allIds.size(),
                        cell.passed(), cell.samples()));
            }
            table.append(line).append('\n');
        }
        return table;
    }

    private void writeDashboardData(List<RunRecord> verdicts, List<RunRecord> allRecords, List<Row> rows,
            Set<String> agents, List<String> projects, double knownSpend, boolean spendPartial,
            Map<String, List<RunRecord>> infraOnly) {
        Path dashboard = repoRoot.resolve("dashboard");
        if (!Files.isDirectory(dashboard)) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("generated", Instant.now().toString());
        data.put("sample", false);
        data.put("spendUsd", knownSpend);
        data.put("spendPartial", spendPartial);
        double estSpend = estimatedSpend(verdicts);
        data.put("estSpendUsd", estSpend > 0 ? estSpend : null);
        data.put("runs", runsHistory(allRecords));
        data.put("noVerdict", infraOnly.entrySet().stream().map(entry -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("agent", entry.getKey());
            item.put("model", entry.getValue().getFirst().model());
            item.put("samples", entry.getValue().size());
            item.put("kinds", entry.getValue().stream().map(r -> r.failureKind() == null ? "unknown" : r.failureKind())
                    .distinct().sorted().toList());
            return item;
        }).toList());
        data.put("agents", rows);
        data.put("projects", projects);

        Map<String, Map<String, Map<String, Integer>>> byProject = new LinkedHashMap<>();
        for (String agent : agents) {
            Map<String, Map<String, Integer>> perProject = new LinkedHashMap<>();
            for (String project : projects) {
                List<String> allIds = catalog.all().stream().filter(e -> e.project().equals(project))
                        .map(EvalDefinition::id).toList();
                List<RunRecord> mine = verdicts.stream().filter(r -> r.agent().equals(agent))
                        .filter(r -> allIds.contains(r.eval())).toList();
                Set<String> attempted = new LinkedHashSet<>(mine.stream().map(RunRecord::eval).toList());
                Cell cell = cell(mine);
                Map<String, Integer> summary = new LinkedHashMap<>();
                summary.put("passed", cell.passed());
                summary.put("functional", cell.functional());
                summary.put("samples", cell.samples());
                summary.put("evals", attempted.size());
                summary.put("totalEvals", allIds.size());
                perProject.put(project, summary);
            }
            byProject.put(agent, perProject);
        }
        data.put("byProject", byProject);

        // Drill-down cells: passed and functional counts over samples; an absent eval was not run.
        Map<String, Map<String, Map<String, Integer>>> byEval = new LinkedHashMap<>();
        for (String agent : agents) {
            Map<String, Map<String, Integer>> perEval = new LinkedHashMap<>();
            List<RunRecord> mine = verdicts.stream().filter(r -> r.agent().equals(agent)).toList();
            for (String evalId : new LinkedHashSet<>(mine.stream().map(RunRecord::eval).toList())) {
                Cell cell = cell(mine.stream().filter(r -> r.eval().equals(evalId)).toList());
                Map<String, Integer> summary = new LinkedHashMap<>();
                summary.put("passed", cell.passed());
                summary.put("functional", cell.functional());
                summary.put("samples", cell.samples());
                perEval.put(evalId, summary);
            }
            byEval.put(agent, perEval);
        }
        data.put("byEval", byEval);

        // Insertion-ordered so regeneration with unchanged results is byte-stable.
        data.put("evals", catalog.all().stream().map(eval -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", eval.id());
            entry.put("project", eval.project());
            entry.put("title", eval.title());
            entry.put("type", eval.meta().getOrDefault("type", ""));
            entry.put("difficulty", eval.meta().getOrDefault("difficulty", ""));
            entry.put("pilot", Boolean.parseBoolean(eval.meta().getOrDefault("pilot", "false")));
            return entry;
        }).toList());
        mapper.writeValue(dashboard.resolve("data.json").toFile(), data);
        System.out.println("wrote dashboard/data.json");
    }

    /** API-price stand-in (samples x configured estimate) when CLIs report no dollars. */
    private double estimatedSpend(List<RunRecord> records) {
        Map<String, Double> estimates = new LinkedHashMap<>();
        try {
            for (Agents.AgentSpec spec : new Agents(repoRoot).loadAll()) {
                if (spec.estCostPerAttemptUsd() != null) {
                    estimates.put(spec.name(), spec.estCostPerAttemptUsd());
                }
            }
        } catch (RuntimeException e) {
            return 0;
        }
        return records.stream()
                .map(r -> estimates.get(r.agent()))
                .filter(est -> est != null)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    /** Unlike the leaderboard, history keeps records from older content identities. */
    private List<Map<String, Object>> runsHistory(List<RunRecord> allRecords) {
        Map<String, List<RunRecord>> byCampaign = new LinkedHashMap<>();
        for (RunRecord record : allRecords) {
            if (record.campaignId() != null) {
                byCampaign.computeIfAbsent(record.campaignId(), id -> new ArrayList<>()).add(record);
            }
        }
        List<Map<String, Object>> runs = new ArrayList<>();
        for (var entry : byCampaign.entrySet()) {
            List<RunRecord> records = entry.getValue();
            String started = records.stream().map(RunRecord::timestamp).filter(t -> t != null)
                    .min(String::compareTo).orElse("");
            Map<String, Object> run = new LinkedHashMap<>();
            run.put("id", entry.getKey());
            run.put("started", started);
            run.put("agents", records.stream().map(RunRecord::agent).distinct().sorted().toList());
            run.put("evals", records.stream().map(RunRecord::eval).distinct().count());
            run.put("samples", records.size());
            run.put("passed", records.stream().filter(RunRecord::passed).count());
            run.put("functional", records.stream().filter(r -> r.isVerdict() && r.functional()).count());
            run.put("recordedCostUsd", records.stream().map(RunRecord::costUsd).filter(c -> c != null)
                    .mapToDouble(Double::doubleValue).sum());
            run.put("benchmarkVersion", records.stream().map(RunRecord::benchmarkVersion)
                    .filter(v -> v != null).findFirst().orElse(null));
            Path notes = repoRoot.resolve("results").resolve("runs").resolve(entry.getKey() + ".notes.md");
            if (Files.exists(notes)) {
                try {
                    run.put("findings", Files.readString(notes).strip());
                } catch (IOException e) {
                    // notes are optional; skip unreadable ones
                }
            }
            run.put("detail", records.stream().map(record -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("agent", record.agent());
                item.put("eval", record.eval());
                item.put("sample", record.sample());
                item.put("outcome", record.effectiveOutcome());
                item.put("passed", record.passed());
                item.put("testsPassed", record.effectiveTestsPassed());
                item.put("idiomatic", record.effectiveIdiomatic());
                item.put("agentExitCode", record.agentExitCode());
                item.put("agentTimedOut", record.agentTimedOut());
                item.put("failureKind", record.failureKind());
                item.put("durationMs", record.agentDurationMs());
                item.put("costUsd", record.costUsd());
                item.put("totalTokens", record.totalTokens());
                return item;
            }).toList());
            runs.add(run);
        }
        runs.sort((a, b) -> String.valueOf(b.get("started")).compareTo(String.valueOf(a.get("started"))));
        // Cap the dashboard payload; results.json remains the complete record.
        return runs.size() > 50 ? runs.subList(0, 50) : runs;
    }

    /** Human-readable per-run logs at results/runs/&lt;name&gt;.md. */
    private void writeRunLogs(List<RunRecord> allRecords) {
        Map<String, List<RunRecord>> byCampaign = new LinkedHashMap<>();
        for (RunRecord record : allRecords) {
            if (record.campaignId() != null) {
                byCampaign.computeIfAbsent(record.campaignId(), id -> new ArrayList<>()).add(record);
            }
        }
        if (byCampaign.isEmpty()) {
            return;
        }
        Path runsDir = repoRoot.resolve("results").resolve("runs");
        try {
            Files.createDirectories(runsDir);
            for (var entry : byCampaign.entrySet()) {
                StringBuilder log = new StringBuilder();
                List<RunRecord> records = entry.getValue();
                String started = records.stream().map(RunRecord::timestamp).filter(t -> t != null)
                        .min(String::compareTo).orElse("unknown");
                long passed = records.stream().filter(RunRecord::passed).count();
                long functionalOnly = records.stream()
                        .filter(r -> "functional_only".equals(r.effectiveOutcome())).count();
                log.append("# Run: ").append(entry.getKey()).append("\n\n");
                log.append("Started ").append(started).append(". ")
                        .append(passed).append(" of ").append(records.size()).append(" samples passed")
                        .append(functionalOnly > 0 ? ", " + functionalOnly + " functional only" : "")
                        .append(". Harness ").append(records.getFirst().benchmarkVersion()).append(".\n");
                Path notes = runsDir.resolve(entry.getKey() + ".notes.md");
                if (Files.exists(notes)) {
                    log.append("\n## Findings\n\n").append(Files.readString(notes).strip()).append('\n');
                }
                for (RunRecord r : records) {
                    log.append("\n## ").append(r.agent()).append(" · ").append(r.eval())
                            .append(" · sample ").append(r.sample())
                            .append(" · ").append(r.effectiveOutcome()).append("\n\n");
                    log.append("- model: ").append(r.model()).append(" (").append(r.provider())
                            .append(", CLI ").append(r.cliVersion()).append(")\n");
                    log.append("- duration: ").append(r.agentDurationMs() == null ? "n/a"
                            : (r.agentDurationMs() / 1000) + "s")
                            .append(", tokens: ").append(r.totalTokens() == null ? "n/a" : r.totalTokens())
                            .append(", cost: ").append(r.costUsd() == null ? "n/a" : "$" + r.costUsd()).append("\n");
                    if (r.effectiveTestsPassed() != null || r.effectiveIdiomatic() != null) {
                        log.append("- hidden tests: ").append(yesNo(r.effectiveTestsPassed()))
                                .append(", idiom checks: ").append(yesNo(r.effectiveIdiomatic()))
                                .append(RunRecord.IDIOM_UNTESTED.equals(r.effectiveOutcome())
                                        ? " (pre-0.6.0 judge stopped before the tests)" : "")
                                .append("\n");
                    }
                    if (r.agentExitCode() != null && r.agentExitCode() != 0
                            || Boolean.TRUE.equals(r.agentTimedOut())) {
                        log.append("- agent CLI: exit ").append(r.agentExitCode())
                                .append(Boolean.TRUE.equals(r.agentTimedOut()) ? ", timed out" : "").append("\n");
                    }
                    if (!r.passed()) {
                        log.append("- failure kind: ").append(r.failureKind() == null ? "unknown" : r.failureKind())
                                .append("\n");
                        if (r.failureReason() != null) {
                            log.append("- failure reason: ").append(r.failureReason().strip()).append("\n");
                        }
                    }
                    log.append("- workspace (until temp cleanup): ").append(r.workspace()).append("\n");
                    if (r.agentResponse() != null && !r.agentResponse().isBlank()) {
                        log.append("\nAgent's closing summary:\n\n```\n")
                                .append(r.agentResponse().strip()).append("\n```\n");
                    }
                }
                Files.writeString(runsDir.resolve(entry.getKey() + ".md"), log.toString());
            }
            System.out.println("wrote results/runs/ (" + byCampaign.size() + " run log(s))");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String yesNo(Boolean value) {
        return value == null ? "not run" : value ? "passed" : "failed";
    }

    private static Long totalTokens(RunRecord record) {
        if (record.totalTokens() != null) {
            return record.totalTokens();
        }
        return record.inputTokens() != null && record.outputTokens() != null
                ? record.inputTokens() + record.outputTokens() : null;
    }

    /** 95% Wilson score interval for a binomial proportion. */
    static double[] wilson(int successes, int trials) {
        if (trials == 0) {
            return new double[] { 0, 1 };
        }
        double z = 1.959963984540054;
        double p = (double) successes / trials;
        double denominator = 1 + z * z / trials;
        double center = (p + z * z / (2 * trials)) / denominator;
        double margin = z * Math.sqrt(p * (1 - p) / trials + z * z / (4.0 * trials * trials)) / denominator;
        return new double[] { Math.max(0, center - margin), Math.min(1, center + margin) };
    }

    private static String pct(double value) {
        return Math.round(value * 100) + "%";
    }

    private static String seconds(Double value) {
        return value == null ? "n/a" : Math.round(value) + "s";
    }

    private static String usd(Double value) {
        return value == null ? "n/a" : "$%.2f".formatted(value);
    }
}
