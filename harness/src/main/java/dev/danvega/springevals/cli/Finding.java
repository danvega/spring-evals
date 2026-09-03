package dev.danvega.springevals.cli;

/** One doctor line; credential values are never part of the message. */
public record Finding(Level level, String message) {

    public enum Level {
        READY, WARNING, BLOCKED
    }

    public static Finding ready(String message) {
        return new Finding(Level.READY, message);
    }

    public static Finding warning(String message) {
        return new Finding(Level.WARNING, message);
    }

    public static Finding blocked(String message) {
        return new Finding(Level.BLOCKED, message);
    }
}
