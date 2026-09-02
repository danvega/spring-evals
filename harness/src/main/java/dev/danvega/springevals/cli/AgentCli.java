package dev.danvega.springevals.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import dev.danvega.springevals.Agents.AgentSpec;

/**
 * One coding-agent CLI as the benchmark drives it inside the sandbox image.
 * Implementations are discovered through ServiceLoader; the id is the
 * "provider" value in agents/*.json. Everything here is measurement: the
 * headless command decides how the agent runs, so the whole package is
 * part of the benchmark content hash.
 */
public interface AgentCli {

    String id();

    /** Executable name inside the image. */
    String binary();

    String npmPackage();

    /** Must match the version installed by harness/docker/Dockerfile. */
    String pinnedVersion();

    List<String> headlessCommand(String prompt, String model);

    /** Host files copied into the container before the agent starts; nothing else from the host reaches it. */
    default List<SeedFile> seedFiles(AgentSpec spec, Path hostHome) {
        return List.of();
    }

    /** The CLI's combined stdout and stderr; null fields where the CLI exposes nothing headlessly. */
    AgentOutput parse(String output, int exitCode);

    /**
     * Provider-specific readiness. Only containerEnv (the expanded agent config
     * env) and seeded files reach the container, so host logins do not count.
     */
    List<Finding> doctor(AgentSpec spec, Map<String, String> containerEnv, HostProbe host);

    /** Attempts in one lane run serially because provider rate limits apply per account. */
    default String lane() {
        return id();
    }

    record SeedFile(Path hostPath, String containerPath) {
    }

    record AgentOutput(String responseText, Double costUsd, Long inputTokens, Long outputTokens) {
    }

    static List<AgentCli> all() {
        return ServiceLoader.load(AgentCli.class).stream().map(ServiceLoader.Provider::get).toList();
    }

    static List<String> ids() {
        return all().stream().map(AgentCli::id).toList();
    }

    static AgentCli forProvider(String id) {
        return all().stream().filter(cli -> cli.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown provider '%s' (supported: %s)".formatted(id, String.join(", ", ids()))));
    }
}
