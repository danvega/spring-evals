package dev.danvega.springevals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportsTest {

    @TempDir
    Path temp;

    @Test
    void wilsonIntervalMakesSmallSampleUncertaintyVisible() {
        double[] interval = Reports.wilson(9, 9);

        assertTrue(interval[0] < 0.75, "nine tasks must not imply a precise 100% capability estimate");
        assertEquals(1.0, interval[1], 0.0001);
    }

    @Test
    void wilsonHandlesNoTrials() {
        double[] interval = Reports.wilson(0, 0);

        assertEquals(0.0, interval[0]);
        assertEquals(1.0, interval[1]);
    }

    @Test
    void writesOnlyContentVersionedCoverageAwareResults() throws Exception {
        Path evalDir = temp.resolve("evals/boot/000-example");
        Files.createDirectories(evalDir.resolve("project"));
        Files.createDirectories(evalDir.resolve("EVAL"));
        Files.createDirectories(temp.resolve("agents"));
        Files.createDirectories(temp.resolve("dashboard"));
        Files.writeString(evalDir.resolve("eval.yaml"), """
                name: 000-example
                project: boot
                title: Example
                type: fix
                difficulty: easy
                """);
        Files.writeString(evalDir.resolve("PROMPT.md"), "fix it");
        Files.writeString(temp.resolve("agents/example.json"), "{}\n");
        EvalDefinition eval = new EvalCatalog(temp).all().getFirst();
        ResultStore.RunRecord record = new ResultStore.RunRecord(
                "example", "model", eval.id(), "boot", 1, true, 1000L, 0.25, "/tmp/ws",
                "2026-08-04T00:00:00Z", "run", "provider", "agent", ContentHashes.eval(eval),
                ContentHashes.agent(temp, "example"), ContentHashes.benchmark(temp), null, null,
                "25", "test-os", "test-arch", "cli 1", "closed", 1, 100L, 50L, 150L,
                "candidate-hash", "done", "campaign-1");

        new Reports(temp, new EvalCatalog(temp)).print(List.of(record));

        String leaderboard = Files.readString(temp.resolve("results/leaderboard.md"));
        String dashboard = Files.readString(temp.resolve("dashboard/data.json"));
        assertTrue(leaderboard.contains("1/1"));
        assertTrue(leaderboard.contains("Avg Task Tokens"));
        assertTrue(dashboard.contains("\"avgTokens\" : 150"));
    }
}
