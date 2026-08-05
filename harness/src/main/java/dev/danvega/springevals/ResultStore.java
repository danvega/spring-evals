package dev.danvega.springevals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

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
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.ALWAYS);

    public ResultStore(Path repoRoot) {
        this.resultsFile = repoRoot.resolve("results").resolve("results.json");
    }

    public List<RunRecord> load() {
        if (!Files.exists(resultsFile)) {
            return new ArrayList<>();
        }
        try {
            return mapper.readValue(resultsFile.toFile(), new TypeReference<List<RunRecord>>() {
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
