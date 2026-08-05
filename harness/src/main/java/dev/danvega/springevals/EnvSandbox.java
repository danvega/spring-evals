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
 * Host-mode contamination barrier (never weaken): agent env must go through the
 * real process environment; SDK option env is a dead store in agent-claude 0.16.0.
 */
final class EnvSandbox {

    private EnvSandbox() {
    }

    record Scope(Map<String, String> previousValues, Set<String> previouslyAbsent) {
    }

    /** Overrides win over removals for the same key; returns a scope for restore(). */
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

    /** Run before any paid spend: proves mutations reach System.getenv and child processes. */
    static void selfTest() {
        // Unique key per invocation so a same-named host variable cannot skew restore checks.
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
