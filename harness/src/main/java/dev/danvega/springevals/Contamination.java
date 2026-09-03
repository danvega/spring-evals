package dev.danvega.springevals;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Flags a transcript that touched what the agent should never have seen. A
 * flag is evidence for a human, never a verdict: the sample keeps its outcome.
 */
final class Contamination {

    private Contamination() {
    }

    static List<String> scan(String transcript, EvalDefinition eval) {
        List<String> flags = new ArrayList<>();
        if (transcript == null || transcript.isBlank()) {
            return flags;
        }
        flag(flags, transcript, Pattern.compile("danvega/spring-evals"), "references the benchmark repository");
        flag(flags, transcript, Pattern.compile("(?<![\\w-])spring-evals(?![\\w-])"), "mentions spring-evals");
        String dirName = eval.dir().getFileName().toString();
        flag(flags, transcript, Pattern.compile(Pattern.quote(eval.id())), "mentions the eval id " + eval.id());
        flag(flags, transcript, Pattern.compile("(?<![\\w-])" + Pattern.quote(dirName) + "(?![\\w-])"),
                "mentions the eval directory " + dirName);
        for (String dir : List.of("SOLUTION/", "EVAL/", "ALTERNATIVES/", "WORKAROUNDS/")) {
            flag(flags, transcript, Pattern.compile("(?<![\\w])" + Pattern.quote(dir)), "references " + dir);
        }
        return flags;
    }

    private static void flag(List<String> flags, String transcript, Pattern pattern, String reason) {
        if (pattern.matcher(transcript).find()) {
            flags.add(reason);
        }
    }
}
