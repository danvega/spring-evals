package dev.danvega.springevals;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

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
