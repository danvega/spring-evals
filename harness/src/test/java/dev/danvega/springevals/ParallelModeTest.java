package dev.danvega.springevals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParallelModeTest {

    @Test
    void defaultsToFourConcurrentContainers() {
        assertEquals(4, Main.resolveParallel(null));
    }

    @Test
    void parallelValueMustBeAnIntegerBetweenOneAndEight() {
        assertEquals(1, Main.resolveParallel("1"));
        assertEquals(8, Main.resolveParallel("8"));
        assertThrows(IllegalArgumentException.class, () -> Main.resolveParallel("0"));
        assertThrows(IllegalArgumentException.class, () -> Main.resolveParallel("9"));
        assertThrows(IllegalArgumentException.class, () -> Main.resolveParallel("true"));
    }
}
