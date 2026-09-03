package dev.danvega.springevals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** One eval on disk; the id is "project/dirname", e.g. "boot/000-initializr-parity". */
public record EvalDefinition(String id, String project, Path dir, Map<String, String> meta) {

    public Path promptFile() {
        return dir.resolve("PROMPT.md");
    }

    public Path projectDir() {
        return dir.resolve("project");
    }

    public Path solutionDir() {
        return dir.resolve("SOLUTION");
    }

    /** Legitimate solutions that differ from SOLUTION in mechanism; validate requires each to reach pass. */
    public Path alternativesDir() {
        return dir.resolve("ALTERNATIVES");
    }

    /** Candidates that pass the hidden tests but miss the idiom; validate requires each to reach functional_only. */
    public Path workaroundsDir() {
        return dir.resolve("WORKAROUNDS");
    }

    /** One reference candidate and the outcome validate expects from it. */
    public record Candidate(String label, Path dir, Judgment.Outcome expected) {
    }

    /** SOLUTION first, then ALTERNATIVES and WORKAROUNDS in name order. Neither extra directory is hashed. */
    public List<Candidate> candidates() {
        List<Candidate> candidates = new ArrayList<>();
        candidates.add(new Candidate("SOLUTION", solutionDir(), Judgment.Outcome.PASS));
        candidates.addAll(subdirectories(alternativesDir(), "ALTERNATIVES", Judgment.Outcome.PASS));
        candidates.addAll(subdirectories(workaroundsDir(), "WORKAROUNDS", Judgment.Outcome.FUNCTIONAL_ONLY));
        return candidates;
    }

    private static List<Candidate> subdirectories(Path parent, String label, Judgment.Outcome expected) {
        if (!Files.isDirectory(parent)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(parent)) {
            return children.filter(Files::isDirectory).sorted()
                    .map(child -> new Candidate(label + "/" + child.getFileName(), child, expected))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path evalTestsDir() {
        return dir.resolve("EVAL");
    }

    public Duration agentTimeout() {
        return Duration.ofSeconds(Long.parseLong(meta.getOrDefault("timeout_seconds", "900")));
    }

    public String title() {
        return meta.getOrDefault("title", id);
    }

    public String summaryLine() {
        return "%s%s  [%s/%s, %s]  %s".formatted(id,
                Boolean.parseBoolean(meta.getOrDefault("pilot", "false")) ? "  [pilot]" : "",
                meta.get("category"), meta.get("type"),
                meta.get("difficulty"), title());
    }
}
