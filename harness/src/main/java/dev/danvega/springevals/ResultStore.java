package dev.danvega.springevals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Append-only run records in results/results.json. New provenance fields stay
 * nullable so legacy records load but never match the content-versioned cache.
 */
public class ResultStore {

    /**
     * One judged sample. Legacy records (attempt, campaignAttempts, no outcome)
     * still load: the aliases map the old names and effectiveOutcome() derives
     * the outcome from passed and failureKind.
     */
    public record RunRecord(String agent, String model, String eval, String project,
            @JsonAlias("attempt") int sample,
            boolean passed, Long agentDurationMs, Double costUsd, String workspace, String timestamp,
            String runId, String provider, String track, String evalHash, String agentConfigHash,
            String benchmarkVersion, String failureKind, String failureReason,
            String javaVersion, String osName, String osArch, String cliVersion,
            String networkPolicy, @JsonAlias("campaignAttempts") int campaignSamples,
            Long inputTokens, Long outputTokens, Long totalTokens,
            String candidateHash, String agentResponse, String campaignId,
            String outcome, Boolean testsPassed, Boolean idiomatic) {

        public String effectiveOutcome() {
            if (outcome != null) {
                return outcome;
            }
            if (passed) {
                return "pass";
            }
            return failureKind == null ? "test_failure"
                    : "idiom_failure".equals(failureKind) ? "functional_only" : failureKind;
        }

        /** Verdict samples are the ones where the agent produced a judged candidate. */
        public boolean isVerdict() {
            return !"agent_error".equals(failureKind) && !"judge_error".equals(failureKind);
        }

        /** Tests passed, whether or not the idiom held. */
        public boolean functional() {
            String effective = effectiveOutcome();
            return "pass".equals(effective) || "functional_only".equals(effective);
        }
    }

    private final Path resultsFile;
    // A record may carry fields this harness does not know (written by a later harness, or since removed); they load anyway.
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
