package dev.danvega.springevals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerSandboxTest {

    @Test
    void imageTagIsContentAddressedFromTheDockerfile() {
        java.nio.file.Path root = java.nio.file.Path.of("").toAbsolutePath().getParent();
        String tag = DockerSandbox.imageTag(root);
        assertTrue(tag.startsWith(DockerSandbox.IMAGE_NAME + ":"));
        assertEquals(DockerSandbox.IMAGE_NAME.length() + 1 + 12, tag.length());
    }

    @Test
    void prunesOnlyContainersWhoseOwnerProcessIsDead() {
        assertTrue(DockerSandbox.ownerAlive(String.valueOf(ProcessHandle.current().pid())));
        assertTrue(!DockerSandbox.ownerAlive(""));
        assertTrue(!DockerSandbox.ownerAlive("not-a-pid"));
        assertTrue(!DockerSandbox.ownerAlive("999999999"));
    }

    @Test
    void judgeCommandIsTheSingleSharedDefinition() {
        assertEquals("./mvnw -B -ntp -Dmaven.test.skip=false -DskipTests=false clean test",
                String.join(" ", MavenJudge.JUDGE_COMMAND));
    }
}
