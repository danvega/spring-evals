package dev.danvega.springevals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import dev.danvega.springevals.Agents.AgentSpec;
import dev.danvega.springevals.ResultStore.RunRecord;
import dev.danvega.springevals.cli.AgentCli;

/**
 * Lanes run concurrently; attempts within a lane stay strictly serial
 * because provider rate limits apply per account.
 */
final class RunScheduler {

    private RunScheduler() {
    }

    /** Lane order and in-lane spec order both follow the resolved spec order. */
    static LinkedHashMap<String, List<AgentSpec>> partitionByLane(List<AgentSpec> specs) {
        LinkedHashMap<String, List<AgentSpec>> lanes = new LinkedHashMap<>();
        for (AgentSpec spec : specs) {
            String lane = AgentCli.forProvider(spec.provider()).lane();
            lanes.computeIfAbsent(lane, ignored -> new ArrayList<>()).add(spec);
        }
        return lanes;
    }

    /**
     * One thread per lane, capped at maxParallel concurrent lanes (each lane
     * runs at most one container at a time); the first lane failure is
     * rethrown only after every lane has finished, so no attempt is orphaned.
     */
    static void runLanes(Map<String, List<AgentSpec>> lanes, int maxParallel,
            BiConsumer<String, List<AgentSpec>> laneBody) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(lanes.size(), maxParallel)));
        try {
            List<Future<?>> futures = new ArrayList<>();
            lanes.forEach((lane, specs) -> futures.add(pool.submit(() -> laneBody.accept(lane, specs))));
            RuntimeException failure = null;
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    if (failure == null) {
                        failure = e.getCause() instanceof RuntimeException runtime ? runtime
                                : new IllegalStateException(e.getCause());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while waiting for lanes", e);
                }
            }
            if (failure != null) {
                throw failure;
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** Reservation and cap check are one atomic step so lanes cannot jointly overshoot the cap. */
    static final class CostReserver {

        private final double cap;
        private double reserved;

        CostReserver(double cap) {
            this.cap = cap;
        }

        synchronized boolean reserve(double estimate) {
            if (reserved + estimate > cap) {
                return false;
            }
            reserved += estimate;
            return true;
        }

        /** Reported actual spend above the estimate still counts against the cap. */
        synchronized void absorbOverrun(double delta) {
            reserved += delta;
        }

        synchronized double reserved() {
            return reserved;
        }

        double cap() {
            return cap;
        }
    }

    /** Every read and mutation of the shared results list is serialized here; saves stay per-attempt. */
    static final class ResultsLedger {

        private final List<RunRecord> records;
        private final Consumer<List<RunRecord>> saver;

        ResultsLedger(List<RunRecord> initial, Consumer<List<RunRecord>> saver) {
            this.records = new ArrayList<>(initial);
            this.saver = saver;
        }

        synchronized List<RunRecord> matching(Predicate<RunRecord> predicate) {
            return records.stream().filter(predicate).toList();
        }

        synchronized void removeMatching(Predicate<RunRecord> predicate) {
            records.removeIf(predicate);
        }

        synchronized void append(RunRecord record) {
            records.add(record);
            saver.accept(List.copyOf(records));
        }

        synchronized List<RunRecord> snapshot() {
            return List.copyOf(records);
        }
    }
}
