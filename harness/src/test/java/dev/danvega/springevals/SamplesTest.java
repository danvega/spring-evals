package dev.danvega.springevals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SamplesTest {

    @Test
    void defaultsToThreeSamplesPerCell() {
        assertEquals(3, Main.resolveSamples(Map.of()));
    }

    @Test
    void samplesMustBeAnIntegerBetweenOneAndTen() {
        assertEquals(1, Main.resolveSamples(Map.of("samples", "1")));
        assertEquals(10, Main.resolveSamples(Map.of("samples", "10")));
        assertThrows(IllegalArgumentException.class, () -> Main.resolveSamples(Map.of("samples", "0")));
        assertThrows(IllegalArgumentException.class, () -> Main.resolveSamples(Map.of("samples", "11")));
        assertThrows(IllegalArgumentException.class, () -> Main.resolveSamples(Map.of("samples", "true")));
    }

    @Test
    void theRetiredAttemptsFlagIsRefusedWithGuidance() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> Main.resolveSamples(Map.of("attempts", "4")));
        assertTrue(error.getMessage().contains("--samples"));
    }
}
