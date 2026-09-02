package dev.danvega.springevals;

/** Verdict of one judged workspace; ERROR means the judge itself failed, never the candidate. */
public record Judgment(Status status, String reasoning, String output) {

    public enum Status {
        PASS, FAIL, ERROR
    }

    public static Judgment pass(String reasoning, String output) {
        return new Judgment(Status.PASS, reasoning, output);
    }

    public static Judgment fail(String reasoning) {
        return new Judgment(Status.FAIL, reasoning, null);
    }

    public static Judgment fail(String reasoning, String output) {
        return new Judgment(Status.FAIL, reasoning, output);
    }

    public static Judgment error(String reasoning, Throwable cause) {
        return new Judgment(Status.ERROR, reasoning + ": " + cause, null);
    }

    public boolean pass() {
        return status == Status.PASS;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }
}
