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
    }
}
