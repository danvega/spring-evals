package dev.danvega.springevals;

/**
 * Verdict of one judged workspace. Tests and idiom checks are recorded
 * separately so a working but non-idiomatic candidate is never confused
 * with a broken one. JUDGE_ERROR means the judge itself failed, never
 * the candidate.
 */
public record Judgment(Outcome outcome, Boolean testsPassed, Boolean idiomatic, String reasoning, String output) {

    public enum Outcome {
        PASS("pass"),
        FUNCTIONAL_ONLY("functional_only"),
        TEST_FAILURE("test_failure"),
        COMPILE_FAILURE("compile_failure"),
        POLICY_FAILURE("policy_failure"),
        JUDGE_ERROR("judge_error");

        private final String recordValue;

        Outcome(String recordValue) {
            this.recordValue = recordValue;
        }

        /** The string stored on run records and compared against eval.yaml's baseline_failure. */
        public String recordValue() {
            return recordValue;
        }

        public static Outcome fromRecordValue(String value) {
            for (Outcome outcome : values()) {
                if (outcome.recordValue.equals(value)) {
                    return outcome;
                }
            }
            throw new IllegalArgumentException("unknown outcome: " + value);
        }
    }

    /** Integrity violation (test suppression, pinned fixture edits, missing test evidence); tests are not trusted. */
    public static Judgment policyFailure(String reasoning) {
        return new Judgment(Outcome.POLICY_FAILURE, null, null, reasoning, null);
    }

    public static Judgment policyFailure(String reasoning, String output) {
        return new Judgment(Outcome.POLICY_FAILURE, false, null, reasoning, output);
    }

    public static Judgment error(String reasoning, Throwable cause) {
        return new Judgment(Outcome.JUDGE_ERROR, null, null, reasoning + ": " + cause, null);
    }

    public boolean pass() {
        return outcome == Outcome.PASS;
    }

    public boolean isError() {
        return outcome == Outcome.JUDGE_ERROR;
    }

    /** The failure kind stored on a record; null for a pass. */
    public String failureKind() {
        return switch (outcome) {
            case PASS -> null;
            case FUNCTIONAL_ONLY -> "idiom_failure";
            default -> outcome.recordValue();
        };
    }
}
