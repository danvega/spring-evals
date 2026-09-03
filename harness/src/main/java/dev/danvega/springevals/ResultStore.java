package dev.danvega.springevals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Append-only run records in results/results.json. New provenance fields stay
 * nullable so legacy records load but never match the content-versioned cache.
 */
public class ResultStore {

    public record RunRecord(String agent, String model, String eval, String project, int attempt,
            boolean passed, Long agentDurationMs, Double costUsd, String workspace, String timestamp,
            String runId, String provider, String track, String evalHash, String agentConfigHash,
            String benchmarkVersion, String failureKind, String failureReason,
            String javaVersion, String osName, String osArch, String cliVersion,
            String networkPolicy, int campaignAttempts, Long inputTokens, Long outputTokens, Long totalTokens,
            String candidateHash, String agentResponse, String campaignId) {
    }

    private final Path resultsFile;
    // Fields added by later harness versions must not break loading records written by earlier ones.
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public ResultStore(Path repoRoot) {
        this.resultsFile = repoRoot.resolve("results").resolve("results.json");
    }

    public List<RunRecord> load() {
        if (!Files.exists(resultsFile)) {
            return new ArrayList<>();
        }
        return mapper.readValue(resultsFile.toFile(), new TypeReference<List<RunRecord>>() {
        });
    }

    public void save(List<RunRecord> records) {
        try {
            Files.createDirectories(resultsFile.getParent());
            mapper.writeValue(resultsFile.toFile(), records);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
