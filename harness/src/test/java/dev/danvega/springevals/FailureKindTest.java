package dev.danvega.springevals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The judge decides on the workspace; the agent's exit code and timeout are recorded, never decisive. */
class FailureKindTest {

    private static final Judgment PASS = new Judgment(Judgment.Outcome.PASS, true, true, "ok", "");
    private static final Judgment IDIOM_MISS = new Judgment(Judgment.Outcome.FUNCTIONAL_ONLY, true, false, "miss", "");

    @Test
    void agentExitCodeDoesNotOverrideAVerdictOnATouchedWorkspace() {
        assertNull(Main.failureKind(agentRun(120_000L, "agent CLI exited 1", 1, false), PASS, false));
        assertEquals("idiom_failure", Main.failureKind(agentRun(120_000L, "agent timed out after 900s", null, true),
                IDIOM_MISS, false));
    }

    @Test
    void untouchedWorkspaceWithAnAgentErrorIsAnAgentError() {
        assertEquals("agent_error", Main.failureKind(agentRun(300_000L, "agent CLI exited 1", 1, false), PASS, true));
    }

    @Test
    void untouchedWorkspaceAfterATooFastCleanExitIsAnAgentError() {
        assertEquals("agent_error", Main.failureKind(agentRun(5_000L, null, 0, false), IDIOM_MISS, true));
    }

    @Test
    void untouchedWorkspaceAfterALongCleanRunKeepsTheVerdict() {
        assertEquals("idiom_failure", Main.failureKind(agentRun(300_000L, null, 0, false), IDIOM_MISS, true));
    }

    private static Main.AgentRun agentRun(Long durationMs, String error, Integer exitCode, boolean timedOut) {
        return new Main.AgentRun(durationMs, null, error, null, null, null, null, exitCode, timedOut, null, null, java.util.List.of());
    }
}
