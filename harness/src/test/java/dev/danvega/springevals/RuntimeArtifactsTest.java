package dev.danvega.springevals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeArtifactsTest {

    private static final String CLASSPATH = String.join(":",
            "/home/agent/.m2/repository/org/springframework/boot/spring-boot-h2console/4.1.1/spring-boot-h2console-4.1.1.jar",
            "/opt/m2-cache/repository/com/h2database/h2/2.4.240/h2-2.4.240.jar",
            "/opt/m2-cache/repository/org/flywaydb/flyway-core/11.0.0/flyway-core-11.0.0.jar");

    @Test
    void findsAnArtifactByGroupDirectoryAndFileName() {
        assertTrue(RuntimeArtifacts.present(CLASSPATH, "org.springframework.boot:spring-boot-h2console"));
        assertTrue(RuntimeArtifacts.present(CLASSPATH, "com.h2database:h2"));
        assertTrue(RuntimeArtifacts.present(CLASSPATH, "org.flywaydb:flyway-core"));
    }

    @Test
    void doesNotMatchPrefixesOrOtherGroups() {
        assertFalse(RuntimeArtifacts.present(CLASSPATH, "org.springframework.boot:spring-boot-h2"));
        assertFalse(RuntimeArtifacts.present(CLASSPATH, "org.springframework.boot:spring-boot-flyway"));
        assertFalse(RuntimeArtifacts.present(CLASSPATH, "org.flywaydb:flyway"));
        assertFalse(RuntimeArtifacts.present(CLASSPATH, "com.example:h2"));
    }

    @Test
    void emptyClasspathHasNothing() {
        assertFalse(RuntimeArtifacts.present("", "com.h2database:h2"));
    }

    @Test
    void rejectsMalformedCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> RuntimeArtifacts.present(CLASSPATH, "h2"));
        assertThrows(IllegalArgumentException.class, () -> RuntimeArtifacts.present(CLASSPATH, "a:b:c"));
    }
}
