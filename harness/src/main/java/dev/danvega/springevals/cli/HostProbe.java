package dev.danvega.springevals.cli;

import java.nio.file.Path;

/** Read-only view of the host for doctor checks; tests substitute a fake so the real home is never read. */
public interface HostProbe {

    String environment(String name);

    Path home();

    boolean fileExists(Path path);

    /** File text for auth-mode detection, or null when unreadable. Values are never printed. */
    String fileContent(Path path);

    ProbeResult probeLocalModels(String baseUrl, String apiKey, String model);

    record ProbeResult(boolean reachable, boolean modelPresent, String message) {
    }
}
