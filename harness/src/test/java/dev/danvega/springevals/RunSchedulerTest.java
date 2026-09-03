package dev.danvega.springevals;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.danvega.springevals.Agents.AgentSpec;
import dev.danvega.springevals.ResultStore.RunRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunSchedulerTest {

    private static AgentSpec spec(String name, String provider) {
        return new AgentSpec(name, provider, "model", Map.of(), 0.1);
    }

    @Test
    void partitionsIntoLanesPreservingResolvedOrder() {
        var lanes = RunScheduler.partitionByLane(List.of(
                spec("claude-a", "claude"), spec("codex-a", "codex"),
                spec("claude-b", "claude"), spec("gemini-a", "gemini")));

        assertEquals(List.of("claude", "codex", "gemini"), List.copyOf(lanes.keySet()));
        assertEquals(List.of("claude-a", "claude-b"),
                lanes.get("claude").stream().map(AgentSpec::name).toList());
        assertEquals(List.of("codex-a"), lanes.get("codex").stream().map(AgentSpec::name).toList());
    }

    @Test
    void costReservationStaysAtomicUnderConcurrentLanes() throws Exception {
        var reserver = new RunScheduler.CostReserver(5.0);
        AtomicInteger granted = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(20);
        try {
            for (int i = 0; i < 20; i++) {
                pool.submit(() -> {
                    start.await();
                    if (reserver.reserve(1.0)) {
                        granted.incrementAndGet();
                    }
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(5, granted.get(), "exactly the cap's worth of reservations may be granted");
        assertEquals(5.0, reserver.reserved());
    }

    @Test
    void reportedOverrunCountsAgainstTheCap() {
        var reserver = new RunScheduler.CostReserver(2.0);
        assertTrue(reserver.reserve(1.0));
        reserver.absorbOverrun(0.9);
        assertFalse(reserver.reserve(0.2));
        assertTrue(reserver.reserve(0.1));
    }

    @Test
    void ledgerSerializesConcurrentAppendsAndSavesPerAttempt() throws Exception {
        ConcurrentLinkedQueue<Integer> savedSizes = new ConcurrentLinkedQueue<>();
        var ledger = new RunScheduler.ResultsLedger(List.of(), records -> savedSizes.add(records.size()));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            for (int i = 0; i < 40; i++) {
                int attempt = i;
                pool.submit(() -> ledger.append(record("agent-" + attempt)));
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(40, ledger.snapshot().size());
        assertEquals(40, savedSizes.size(), "every append must trigger its own save");
        assertEquals(40, savedSizes.stream().distinct().count(),
                "saves are serialized, so every snapshot has a distinct size");
    }

    @Test
    void ledgerSkipAndForceQueriesSeeOnlyMatchingRecords() {
        var ledger = new RunScheduler.ResultsLedger(List.of(record("keep"), record("drop")), records -> {
        });
        assertEquals(1, ledger.matching(r -> r.agent().equals("drop")).size());
        ledger.removeMatching(r -> r.agent().equals("drop"));
        assertEquals(List.of("keep"), ledger.snapshot().stream().map(RunRecord::agent).toList());
    }

    @Test
    void lanesRunConcurrentlyButNeverExceedTheParallelCap() {
        var lanes = RunScheduler.partitionByLane(List.of(
                spec("a", "claude"), spec("b", "codex"), spec("c", "gemini"), spec("d", "qwen-code")));
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        RunScheduler.runLanes(lanes, 2, (provider, specs) -> {
            peak.accumulateAndGet(active.incrementAndGet(), Math::max);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            active.decrementAndGet();
        });
        assertTrue(peak.get() >= 2, "lanes must actually overlap");
        assertTrue(peak.get() <= 2, "no more than --parallel lanes may run containers at once");
    }

    @Test
    void laneFailureSurfacesOnlyAfterEveryLaneFinishes() {
        var lanes = RunScheduler.partitionByLane(List.of(spec("a", "claude"), spec("b", "codex")));
        AtomicInteger completed = new AtomicInteger();
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> RunScheduler.runLanes(lanes, 4, (provider, specs) -> {
                    if (provider.equals("claude")) {
                        throw new IllegalStateException("lane blew up");
                    }
                    completed.incrementAndGet();
                }));
        assertEquals("lane blew up", thrown.getMessage());
        assertEquals(1, completed.get(), "the healthy lane must run to completion");
    }

    private static RunRecord record(String agent) {
        return new RunRecord(agent, "model", "boot/000-example", "boot", 1, false, null, null, "/tmp/ws",
                "2026-01-01T00:00:00Z", "run-id", "claude", "agent", null, null, null, null, null,
                null, null, null, null, null, 1, null, null, null, null, null, null, null, null, null, null, null);
    }
}
