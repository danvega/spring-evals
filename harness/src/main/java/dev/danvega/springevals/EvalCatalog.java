package dev.danvega.springevals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Discovers evals under evals/&lt;project&gt;/&lt;eval&gt;/ and parses their
 * eval.yaml metadata (a flat "key: value" subset plus "key: &gt;" folded blocks).
 */
public class EvalCatalog {

    private final Path evalsDir;

    public EvalCatalog(Path repoRoot) {
        this.evalsDir = repoRoot.resolve("evals");
    }

    public List<EvalDefinition> all() {
        if (!Files.isDirectory(evalsDir)) {
            return List.of();
        }
        List<EvalDefinition> result = new ArrayList<>();
        try (Stream<Path> projects = Files.list(evalsDir)) {
            for (Path project : projects.filter(Files::isDirectory).sorted().toList()) {
                try (Stream<Path> entries = Files.list(project)) {
                    for (Path entry : entries.filter(Files::isDirectory).sorted().toList()) {
                        if (Files.exists(entry.resolve("eval.yaml"))) {
                            result.add(load(project.getFileName() + "/" + entry.getFileName()));
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        result.sort(Comparator.comparing(EvalDefinition::id));
        return result;
    }

    public EvalDefinition load(String id) {
        Path dir = evalsDir.resolve(id);
        Path yaml = dir.resolve("eval.yaml");
        if (!Files.exists(yaml)) {
            throw new IllegalArgumentException(
                    "eval not found: " + id + " (expected " + yaml + "; ids look like boot/000-jackson3-migration)");
        }
        try {
            return new EvalDefinition(id, id.split("/")[0], dir, parseYaml(Files.readString(yaml)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Structural and metadata validation before any Maven process starts. */
    public List<String> validate(EvalDefinition eval) {
        List<String> errors = new ArrayList<>();
        for (String key : List.of("name", "project", "title", "category", "type", "difficulty",
                "baseline_failure", "boot_version", "timeout_seconds", "description")) {
            if (eval.meta().getOrDefault(key, "").isBlank()) {
                errors.add("missing metadata field: " + key);
            }
        }
        if (!eval.dir().getFileName().toString().equals(eval.meta().get("name"))) {
            errors.add("metadata name must match directory name");
        }
        if (!eval.project().equals(eval.meta().get("project"))) {
            errors.add("metadata project must match suite directory");
        }
        if (!List.of("fix", "build").contains(eval.meta().get("type"))) {
            errors.add("type must be fix or build");
        }
        if (!List.of("easy", "medium", "hard").contains(eval.meta().get("difficulty"))) {
            errors.add("difficulty must be easy, medium, or hard");
        }
        if (!List.of("compile_failure", "test_failure").contains(eval.meta().get("baseline_failure"))) {
            errors.add("baseline_failure must be compile_failure or test_failure");
        }
        if (!List.of("json", "web", "data", "security", "testing", "config", "observability",
                "messaging", "other").contains(eval.meta().get("category"))) {
            errors.add("unsupported category: " + eval.meta().get("category"));
        }
        if (!eval.meta().getOrDefault("boot_version", "").matches("\\d+\\.\\d+\\.\\d+")) {
            errors.add("boot_version must be a pinned x.y.z release");
        }
        for (Path required : List.of(eval.promptFile(), eval.projectDir(), eval.evalTestsDir(), eval.solutionDir(),
                eval.projectDir().resolve("pom.xml"), eval.projectDir().resolve("mvnw"))) {
            if (!Files.exists(required)) {
                errors.add("required path missing: " + eval.dir().relativize(required));
            }
        }
        try {
            if (eval.agentTimeout().isZero() || eval.agentTimeout().isNegative()) {
                errors.add("timeout_seconds must be positive");
            }
        } catch (RuntimeException e) {
            errors.add("timeout_seconds must be an integer");
        }
        return errors;
    }

    static Map<String, String> parseYaml(String text) {
        Map<String, String> meta = new HashMap<>();
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            var matcher = java.util.regex.Pattern.compile("^([a-zA-Z_]+):\\s*(.*)$").matcher(lines[i]);
            if (!matcher.matches()) {
                continue;
            }
            String key = matcher.group(1);
            String value = matcher.group(2).trim();
            if (value.equals(">") || value.equals("|")) {
                StringBuilder block = new StringBuilder();
                while (i + 1 < lines.length && (lines[i + 1].startsWith("  ") || lines[i + 1].isBlank())) {
                    block.append(lines[++i].trim()).append(' ');
                }
                meta.put(key, block.toString().trim());
            } else {
                meta.put(key, value);
            }
        }
        return meta;
    }
}
