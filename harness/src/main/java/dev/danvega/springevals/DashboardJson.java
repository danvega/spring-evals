package dev.danvega.springevals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * The dashboard's only Jackson touchpoint. Callers pass and receive plain
 * maps and lists, so swapping the mapper library is a one-file change.
 */
final class DashboardJson {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final TypeReference<LinkedHashMap<String, Object>> OBJECT = new TypeReference<>() {
    };

    private DashboardJson() {
    }

    static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Map<String, Object> readObject(String text) {
        try {
            Map<String, Object> value = MAPPER.readValue(text, OBJECT);
            return value == null ? new LinkedHashMap<>() : value;
        } catch (IOException e) {
            throw new IllegalArgumentException("expected a JSON object: " + e.getMessage());
        }
    }

    /** A missing file reads as an empty object so callers can add keys without special cases. */
    static Map<String, Object> readObject(Path file) {
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        try {
            return readObject(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void writeObject(Path file, Map<String, Object> value) {
        try {
            Files.writeString(file, write(value) + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object value) {
        return value instanceof List<?> ? (List<Object>) value : null;
    }
}
