package dev.danvega.springevals;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Test-only runner: executes the judged command on the host so judge logic can be exercised without Docker. */
final class LocalBuildRunner implements MavenJudge.BuildRunner {

    @Override
    public MavenJudge.BuildResult run(Path workspace, List<String> command, Duration timeout) {
        try {
            Process process = new ProcessBuilder(command).directory(workspace.toFile())
                    .redirectErrorStream(true).start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return new MavenJudge.BuildResult(-1, "", true);
            }
            return new MavenJudge.BuildResult(process.exitValue(),
                    new String(process.getInputStream().readAllBytes()), false);
        } catch (IOException e) {
            return new MavenJudge.BuildResult(-1, e.getMessage(), false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new MavenJudge.BuildResult(-1, "", true);
        }
    }

    /** For tests that must prove the policy rejects a workspace before any build starts. */
    static MavenJudge.BuildRunner neverInvoked() {
        return (workspace, command, timeout) -> {
            throw new AssertionError("the judged build must not start for this workspace");
        };
    }
}
