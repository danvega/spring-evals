package dev.danvega.springevals;

import java.util.List;
import java.util.Map;

import dev.danvega.springevals.ResultStore.RunRecord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SamplesTest {

    @Test
    void defaultsToThreeSamplesPerCell() {
        assertEquals(3, Main.resolveSamples(Map.of()));
    }

    @Test
    void samplesMustBeAnIntegerBetweenOneAndTen() {
        assertEquals(1, Main.resolveSamples(Map.of("samples", "1")));
        assertEquals(10, Main.resolveSamples(Map.of("samples", "10")));
        assertThrows(IllegalArgumentException.class, () -> Main.resolveSamples(Map.of("samples", "0")));
        assertThrows(IllegalArgumentException.class, () -> Main.resolveSamples(Map.of("samples", "11")));
        assertThrows(IllegalArgumentException.class, () -> Main.resolveSamples(Map.of("samples", "true")));
    }

    @Test
    void onlyVerdictSamplesFillACellAndRerunsTopItUp() {
        List<RunRecord> existing = List.of(record(1, "pass", null), record(2, "agent_error", "agent_error"),
                record(3, "test_failure", "test_failure"));

        assertEquals(1, Main.samplesToRun(existing, 3), "two verdicts on record, one more to reach three");
        assertEquals(4, Main.nextSampleNumber(existing), "sample numbers keep counting past the infra failure");
        assertEquals(0, Main.samplesToRun(existing, 2));
        assertEquals(3, Main.samplesToRun(List.of(), 3));
        assertEquals(1, Main.nextSampleNumber(List.of()));
    }

    private static RunRecord record(int sample, String outcome, String failureKind) {
        return new RunRecord("a", "m", "boot/000-example", "boot", sample, "pass".equals(outcome), 1000L, null,
                "/tmp/ws", "2026-09-02T00:00:0" + sample + "Z", "run", "claude", "agent", null, null, null,
                failureKind, null, null, null, null, null, null, 3, null, null, null, null, null, "c",
                outcome, null, null, null, null, null, null, null, null);
    }

    @Test
    void theRetiredAttemptsFlagIsRefusedWithGuidance() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> Main.resolveSamples(Map.of("attempts", "4")));
        assertTrue(error.getMessage().contains("--samples"));
    }
}
