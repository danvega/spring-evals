package dev.danvega.springevals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechanismPolicyTest {

    @Test
    void everyReferenceSolutionSatisfiesItsDeterministicPolicy() {
        Path root = Path.of("..").toAbsolutePath().normalize();
        EvalCatalog catalog = new EvalCatalog(root);
        assertTrue(new Agents(root).loadAll().size() >= 1, "agent configs must parse and validate");
        Workspaces workspaces = new Workspaces(root);
        MavenJudge judge = new MavenJudge();

        for (EvalDefinition eval : catalog.all()) {
            assertTrue(catalog.validate(eval).isEmpty(),
                    () -> "invalid eval structure for " + eval.id() + ": " + catalog.validate(eval));
            Path workspace = workspaces.freshCopy(eval, "policy-test");
            try {
                workspaces.applySolution(eval, workspace);
                assertNull(judge.validatePolicy(eval, workspace),
                        () -> "reference solution violates mechanism policy for " + eval.id());
            } finally {
                Workspaces.deleteTree(workspace);
            }
        }
    }
}
