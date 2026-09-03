package dev.danvega.springevals;

import java.util.regex.Pattern;

/**
 * Matches "groupId:artifactId" coordinates against a Maven-resolved classpath.
 * The local repository layout puts every jar at .../g/r/o/u/p/artifactId/version/artifactId-version.jar,
 * so a coordinate is present when one entry carries both the group directory and an artifact file name.
 */
final class RuntimeArtifacts {

    private RuntimeArtifacts() {
    }

    static boolean present(String classpath, String coordinate) {
        String[] parts = coordinate.split(":");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("requiredRuntimeArtifacts entries must be groupId:artifactId: " + coordinate);
        }
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        Pattern entry = Pattern.compile("(?:^|[:;])[^:;]*/" + Pattern.quote(group) + "/" + Pattern.quote(artifact)
                + "/[^/:;]+/" + Pattern.quote(artifact) + "-[^/:;]+\\.jar(?=$|[:;])");
        return entry.matcher(classpath.strip()).find();
    }
}
