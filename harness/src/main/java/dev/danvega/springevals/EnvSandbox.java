package dev.danvega.springevals;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Applies per-attempt environment variables to THIS process so that agent
 * CLIs inherit them. This is the HOST-MODE contamination barrier; docker
 * mode isolates through the container instead.
 *
 * Why this exists: the Agent Client SDKs accept environmentVariables in
 * their options, but agent-claude 0.16.0 never forwards them to the CLI
 * process (a dead store). The CLI transports spawn processes with a
 * fully inherited environment, so the only reliable, provider-agnostic
 * way to control what a host-spawned agent CLI sees is to mutate the
 * harness process environment around each attempt. Never weaken it.
 *
 * Two layers, both required:
 * 1. libc setenv/unsetenv via the FFM API mutates the REAL process
 *    environ, so children inherit the change no matter how they are
 *    spawned (some SDKs pass envp=null, which bypasses Java's cached
 *    env map entirely).
 * 2. java.lang.ProcessEnvironment reflection keeps System.getenv and
 *    ProcessBuilder.environment() views consistent, because the claude
 *    transport re-reads System.getenv for credentials (either
 *    CLAUDE_CODE_OAUTH_TOKEN or ANTHROPIC_API_KEY).
 *
 * Requires --add-opens java.base/java.lang=ALL-UNNAMED and
 * --enable-native-access=ALL-UNNAMED, which the ./spring-evals wrapper
 * sets via MAVEN_OPTS. Call selfTest() before spending money so a missing
 * flag fails the run, not the isolation. Unix-only by design; the
 * benchmark runs on macOS/Linux.
 */
final class EnvSandbox {

    private EnvSandbox() {
    }

    /** Everything needed to undo an apply(). */
    record Scope(Map<String, String> previousValues, Set<String> previouslyAbsent) {
    }

    /**
     * Removes {@code removals}, then sets {@code overrides}, on the real
     * process environment. Returns a scope for restore(). Overrides win
     * over removals for the same key.
     */
    static Scope apply(Map<String, String> overrides, Set<String> removals) {
        Map<String, String> previousValues = new HashMap<>();
        Set<String> previouslyAbsent = new HashSet<>();
        Set<String> touched = new HashSet<>(removals);
        touched.addAll(overrides.keySet());
        for (String key : touched) {
            String current = System.getenv(key);
            if (current == null) {
                previouslyAbsent.add(key);
            } else {
                previousValues.put(key, current);
            }
        }
        for (String key : removals) {
            remove(key);
        }
        overrides.forEach(EnvSandbox::set);
        return new Scope(Map.copyOf(previousValues), Set.copyOf(previouslyAbsent));
    }

    static void restore(Scope scope) {
        scope.previousValues().forEach(EnvSandbox::set);
        for (String key : scope.previouslyAbsent()) {
            remove(key);
        }
    }

    /**
     * Proves the mechanism works end to end: mutates a probe variable,
     * checks both System.getenv and a child process see it, restores it.
     * Throws with a actionable message when reflection is locked down.
     */
    static void selfTest() {
        // Unique key per invocation so a host variable of the same name can
        // never make restore verification lie in either direction.
        String probe = "SPRING_EVALS_ENV_PROBE_" + Long.toHexString(System.nanoTime());
        String value = "probe-" + System.nanoTime();
        Scope scope = apply(Map.of(probe, value), Set.of());
        try {
            if (!value.equals(System.getenv(probe))) {
                throw new IllegalStateException("env mutation not visible to System.getenv");
            }
            Process child = new ProcessBuilder("/bin/sh", "-c", "printf %s \"$" + probe + "\"").start();
            String seen = new String(child.getInputStream().readAllBytes());
            child.waitFor();
            if (!value.equals(seen)) {
                throw new IllegalStateException("env mutation not inherited by child process, saw: " + seen);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("environment sandbox self-test failed", e);
        } finally {
            restore(scope);
        }
        if (System.getenv(probe) != null) {
            throw new IllegalStateException("environment sandbox restore failed");
        }
    }

    private static final Linker LINKER = Linker.nativeLinker();
    private static final MethodHandle SETENV = LINKER.downcallHandle(
            LINKER.defaultLookup().find("setenv").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle UNSETENV = LINKER.downcallHandle(
            LINKER.defaultLookup().find("unsetenv").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private static void set(String key, String value) {
        try (Arena arena = Arena.ofConfined()) {
            int rc = (int) SETENV.invoke(arena.allocateFrom(key), arena.allocateFrom(value), 1);
            if (rc != 0) {
                throw new IllegalStateException("setenv failed for " + key);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw failure(t);
        }
        try {
            mutateJavaView(env -> env.put(variable(key), value(value)));
        } catch (ReflectiveOperationException e) {
            throw failure(e);
        }
    }

    private static void remove(String key) {
        try (Arena arena = Arena.ofConfined()) {
            int rc = (int) UNSETENV.invoke(arena.allocateFrom(key));
            if (rc != 0) {
                throw new IllegalStateException("unsetenv failed for " + key);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw failure(t);
        }
        try {
            mutateJavaView(env -> env.remove(variable(key)));
        } catch (ReflectiveOperationException e) {
            throw failure(e);
        }
    }

    private interface EnvMutation {
        void run(Map<Object, Object> env) throws ReflectiveOperationException;
    }

    @SuppressWarnings("unchecked")
    private static void mutateJavaView(EnvMutation mutation) throws ReflectiveOperationException {
        Class<?> processEnvironment = Class.forName("java.lang.ProcessEnvironment");
        Field field = processEnvironment.getDeclaredField("theEnvironment");
        field.setAccessible(true);
        mutation.run((Map<Object, Object>) field.get(null));
    }

    private static Object variable(String key) throws ReflectiveOperationException {
        Method valueOf = Class.forName("java.lang.ProcessEnvironment$Variable")
                .getDeclaredMethod("valueOf", String.class);
        valueOf.setAccessible(true);
        return valueOf.invoke(null, key);
    }

    private static Object value(String value) throws ReflectiveOperationException {
        Method valueOf = Class.forName("java.lang.ProcessEnvironment$Value")
                .getDeclaredMethod("valueOf", String.class);
        valueOf.setAccessible(true);
        return valueOf.invoke(null, value);
    }

    private static IllegalStateException failure(Throwable e) {
        return new IllegalStateException(
                "Cannot mutate the process environment. Agent isolation depends on it. "
                        + "Run through ./spring-evals (it sets --add-opens java.base/java.lang=ALL-UNNAMED "
                        + "and --enable-native-access=ALL-UNNAMED via MAVEN_OPTS).",
                e);
    }
}
