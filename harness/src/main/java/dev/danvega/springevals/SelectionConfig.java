package dev.danvega.springevals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Gitignored local selection (which agents commands pick up); it never changes
 * how an attempt is measured, so it is deliberately not part of result identity.
 */
final class SelectionConfig {

    static final String FILE_NAME = "spring-evals.local.json";

    private final Set<String> enabledAgents;

    private SelectionConfig(Set<String> enabledAgents) {
        this.enabledAgents = enabledAgents;
    }

    /** Absent file or absent enabledAgents key selects every agent. */
    static SelectionConfig load(Path repoRoot, List<String> knownAgents) {
        Path file = repoRoot.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            return new SelectionConfig(null);
        }
        try {
            JsonNode node = new ObjectMapper().readTree(file.toFile());
            if (!node.has("enabledAgents")) {
                return new SelectionConfig(null);
            }
            if (!node.get("enabledAgents").isArray()) {
                throw new IllegalArgumentException(FILE_NAME + ": enabledAgents must be an array of agent names");
            }
            Set<String> names = new LinkedHashSet<>();
            node.get("enabledAgents").forEach(name -> names.add(name.asText()));
            // A typo would silently drop an agent from every selection; refuse instead.
            for (String name : names) {
                if (!knownAgents.contains(name)) {
                    throw new IllegalArgumentException(FILE_NAME + " enables unknown agent '" + name
                            + "' (no agents/" + name + ".json)");
                }
            }
            return new SelectionConfig(Set.copyOf(names));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    boolean restricts() {
        return enabledAgents != null;
    }

    boolean enabled(String agentName) {
        return enabledAgents == null || enabledAgents.contains(agentName);
    }
}
