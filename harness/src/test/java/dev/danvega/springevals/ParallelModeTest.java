package dev.danvega.springevals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParallelModeTest {

    @Test
    void dockerModeDefaultsToFourConcurrentContainers() {
        assertEquals(4, Main.resolveParallel(null, "docker"));
    }

    @Test
    void hostModeAlwaysRunsSerialWhenParallelIsNotRequested() {
        assertEquals(1, Main.resolveParallel(null, "host"));
    }

    @Test
    void hostModeRefusesAnExplicitParallelRequest() {
        var thrown = assertThrows(IllegalArgumentException.class,
                () -> Main.resolveParallel("4", "host"));
        assertTrue(thrown.getMessage().contains("docker"));
        assertTrue(thrown.getMessage().contains("EnvSandbox"));
    }

    @Test
    void parallelValueMustBeAnIntegerBetweenOneAndEight() {
        assertEquals(1, Main.resolveParallel("1", "docker"));
        assertEquals(8, Main.resolveParallel("8", "docker"));
        assertThrows(IllegalArgumentException.class, () -> Main.resolveParallel("0", "docker"));
        assertThrows(IllegalArgumentException.class, () -> Main.resolveParallel("9", "docker"));
        assertThrows(IllegalArgumentException.class, () -> Main.resolveParallel("true", "docker"));
    }
}
