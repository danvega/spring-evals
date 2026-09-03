package dev.danvega.springevals;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Flags are evidence for a human; the scan must find the repo, the eval, and the hidden directories, and nothing else. */
class ContaminationTest {

    private static final EvalDefinition EVAL = new EvalDefinition("boot/003-jackson3-migration", "boot",
            Path.of("/repo/evals/boot/003-jackson3-migration"), Map.of());

    @Test
    void cleanTranscriptHasNoFlags() {
        String transcript = "{\"type\":\"assistant\",\"text\":\"Checking https://repo1.maven.org for spring-boot-starter-webmvc; "
                + "the evaluation of tools.jackson looks fine; SOLUTIONS considered\"}";
        assertEquals(List.of(), Contamination.scan(transcript, EVAL));
    }

    @Test
    void repoEvalAndHiddenDirectoriesAreFlagged() {
        List<String> flags = Contamination.scan(
                "git clone https://github.com/danvega/spring-evals && cat evals/boot/003-jackson3-migration/SOLUTION/pom.xml", EVAL);
        assertTrue(flags.stream().anyMatch(f -> f.contains("repository")), flags.toString());
        assertTrue(flags.stream().anyMatch(f -> f.contains("eval id")), flags.toString());
        assertTrue(flags.stream().anyMatch(f -> f.contains("eval directory")), flags.toString());
        assertTrue(flags.stream().anyMatch(f -> f.contains("SOLUTION/")), flags.toString());
    }

    @Test
    void evalDirectoryNameIsMatchedAsAWholeWord() {
        assertEquals(List.of("mentions the eval directory 003-jackson3-migration"),
                Contamination.scan("searching for \"003-jackson3-migration\" on github", EVAL));
        assertEquals(List.of(), Contamination.scan("v003-jackson3-migration-old", EVAL));
    }

    @Test
    void blankTranscriptIsClean() {
        assertEquals(List.of(), Contamination.scan(null, EVAL));
        assertEquals(List.of(), Contamination.scan("  ", EVAL));
    }
}
