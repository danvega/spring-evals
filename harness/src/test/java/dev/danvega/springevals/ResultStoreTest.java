package dev.danvega.springevals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResultStoreTest {

    @TempDir
    Path temp;

    @Test
    void readsLegacyRecordsWithoutTreatingThemAsVersioned() throws Exception {
        Path results = temp.resolve("results/results.json");
        Files.createDirectories(results.getParent());
        Files.writeString(results, """
                [{
                  "agent":"old-agent","model":"old-model","eval":"boot/000-example",
                  "project":"boot","attempt":1,"passed":true,"agentDurationMs":1000,
                  "costUsd":0.1,"workspace":"/tmp/old","timestamp":"2026-01-01T00:00:00Z",
                  "fieldFromAFutureHarness":"ignored"
                }]
                """);

        var record = new ResultStore(temp).load().getFirst();

        assertEquals("old-agent", record.agent());
        assertNull(record.evalHash(), "legacy records must not match a content-versioned cache key");
        assertEquals(0.1, record.costUsd(), "a record carrying a field this harness does not know must still load");
        assertEquals(1, record.sample(), "the legacy attempt field maps onto sample");
        assertEquals("pass", record.effectiveOutcome(), "a legacy pass has no outcome field and derives one");
    }

    @Test
    void derivesOutcomesForLegacyFailures() throws Exception {
        Path results = temp.resolve("results/results.json");
        Files.createDirectories(results.getParent());
        Files.writeString(results, """
                [{"agent":"a","model":"m","eval":"boot/000-example","project":"boot","attempt":2,
                  "passed":false,"failureKind":"policy_failure","campaignAttempts":4},
                 {"agent":"a","model":"m","eval":"boot/000-example","project":"boot","attempt":3,
                  "passed":false,"failureKind":"idiom_failure"},
                 {"agent":"a","model":"m","eval":"boot/000-example","project":"boot","sample":1,
                  "passed":true,"outcome":"pass","testsPassed":true,"idiomatic":true,"campaignSamples":3}]
                """);

        var records = new ResultStore(temp).load();

        assertEquals("policy_failure", records.get(0).effectiveOutcome());
        assertEquals(4, records.get(0).campaignSamples(), "the legacy campaignAttempts field maps onto campaignSamples");
        assertEquals("functional_only", records.get(1).effectiveOutcome());
        assertEquals(true, records.get(1).functional());
        assertEquals("pass", records.get(2).effectiveOutcome());
        assertEquals(true, records.get(2).idiomatic());
    }
}
