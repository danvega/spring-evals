package com.example.fieldnotes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hidden eval assertions: repository queries must be generated at build
 * time, before tests run. Surefire runs with user.dir at the project root,
 * so target/ paths are relative.
 */
class BuildTimeQueryEvalTest {

    private static final Path AOT_SOURCES = Path.of("target", "spring-aot", "main", "sources");
    private static final Path CLASSES = Path.of("target", "classes", "com", "example");

    @Test
    void queryCodeIsGeneratedDuringTheBuild() throws IOException {
        assertThat(AOT_SOURCES)
                .as("the build must generate repository code before tests run; "
                        + "target/spring-aot/main/sources is missing")
                .isDirectory();
        try (Stream<Path> files = Files.walk(AOT_SOURCES)) {
            assertThat(files.filter(f -> f.toString().endsWith(".java")).count())
                    .as("build-time processing produced no generated sources")
                    .isPositive();
        }
    }

    @Test
    void everyRepositoryHasBuildTimeQueryMetadata() throws IOException {
        List<Path> metadata;
        try (Stream<Path> files = Files.walk(CLASSES)) {
            metadata = files
                    .filter(f -> f.toString().endsWith(".json"))
                    .filter(f -> !f.toString().contains("META-INF"))
                    .filter(BuildTimeQueryEvalTest::isRepositoryMetadata)
                    .toList();
        }

        assertThat(metadata)
                .as("build-time repository metadata is missing; every repository must be "
                        + "processed during the build (found %s)", metadata)
                .hasSizeGreaterThanOrEqualTo(3);

        List<Path> withoutQueries = metadata.stream()
                .filter(file -> !read(file).contains("\"query\""))
                .toList();
        assertThat(withoutQueries)
                .as("these repositories contributed no build-time query, so a finder was "
                        + "skipped or dropped: %s", withoutQueries)
                .isEmpty();
    }

    private static boolean isRepositoryMetadata(Path file) {
        String text = read(file);
        return text.contains("\"module\"") && text.contains("\"methods\"");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
