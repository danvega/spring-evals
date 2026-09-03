package dev.danvega.springevals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fast policy proof without Maven: SOLUTION and every ALTERNATIVES candidate
 * satisfy their eval's idiom checks, and every WORKAROUNDS candidate misses
 * them. Validate proves the hidden-test half in containers.
 */
class MechanismPolicyTest {

    @Test
    void everyReferenceCandidateHasTheExpectedPolicyOutcome() {
        Path root = Path.of("..").toAbsolutePath().normalize();
        EvalCatalog catalog = new EvalCatalog(root);
        assertTrue(new Agents(root).loadAll().size() >= 1, "agent configs must parse and validate");
        Workspaces workspaces = new Workspaces(root);
        MavenJudge judge = new MavenJudge();

        for (EvalDefinition eval : catalog.all()) {
            assertTrue(catalog.validate(eval).isEmpty(),
                    () -> "invalid eval structure for " + eval.id() + ": " + catalog.validate(eval));
            for (EvalDefinition.Candidate candidate : eval.candidates()) {
                Path workspace = workspaces.freshCopy(eval, "policy-test");
                try {
                    workspaces.applyCandidate(candidate.dir(), workspace);
                    Judgment policy = judge.validatePolicy(eval, workspace);
                    if (candidate.expected() == Judgment.Outcome.PASS) {
                        assertNull(policy, () -> candidate.label() + " violates mechanism policy for " + eval.id()
                                + (policy == null ? "" : ": " + policy.reasoning()));
                    } else {
                        assertTrue(policy != null && policy.outcome() == Judgment.Outcome.FUNCTIONAL_ONLY,
                                () -> candidate.label() + " must miss an idiom check for " + eval.id());
                    }
                } finally {
                    Workspaces.deleteTree(workspace);
                }
            }
        }
    }
}
