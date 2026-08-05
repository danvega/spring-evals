package dev.danvega.springevals;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * These tests prove the isolation mechanism the benchmark depends on:
 * variables applied to the process environment are visible to
 * System.getenv AND inherited by child processes (agent CLIs), and
 * restore puts everything back. If these fail, no paid run can be
 * trusted.
 */
class EnvSandboxTest {

    @Test
    void selfTestPasses() {
        EnvSandbox.selfTest();
    }

    @Test
    void overridesReachChildProcessesAndRestoreCleansUp() throws Exception {
        String key = "SPRING_EVALS_TEST_VAR";
        assertNull(System.getenv(key));

        EnvSandbox.Scope scope = EnvSandbox.apply(Map.of(key, "isolated-value"), Set.of());
        try {
            assertEquals("isolated-value", System.getenv(key));
            assertEquals("isolated-value", readFromChild(key));
        } finally {
            EnvSandbox.restore(scope);
        }
        assertNull(System.getenv(key));
        assertEquals("", readFromChild(key));
    }

    @Test
    void removalsHideHostVariablesFromChildren() throws Exception {
        String key = "SPRING_EVALS_HOST_SECRET";
        EnvSandbox.Scope host = EnvSandbox.apply(Map.of(key, "host-value"), Set.of());
        try {
            EnvSandbox.Scope attempt = EnvSandbox.apply(Map.of(), Set.of(key));
            try {
                assertNull(System.getenv(key));
                assertEquals("", readFromChild(key));
            } finally {
                EnvSandbox.restore(attempt);
            }
            assertEquals("host-value", System.getenv(key));
        } finally {
            EnvSandbox.restore(host);
        }
    }

    @Test
    void overrideOfExistingValueRestoresTheOriginal() {
        String key = "SPRING_EVALS_ORIGINAL";
        EnvSandbox.Scope original = EnvSandbox.apply(Map.of(key, "before"), Set.of());
        try {
            EnvSandbox.Scope override = EnvSandbox.apply(Map.of(key, "after"), Set.of());
            assertEquals("after", System.getenv(key));
            EnvSandbox.restore(override);
            assertEquals("before", System.getenv(key));
        } finally {
            EnvSandbox.restore(original);
        }
    }

    private static String readFromChild(String key) throws Exception {
        Process child = new ProcessBuilder("/bin/sh", "-c", "printf %s \"$" + key + "\"").start();
        String out = new String(child.getInputStream().readAllBytes());
        child.waitFor();
        return out;
    }
}
