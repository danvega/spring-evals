package dev.danvega.springevals;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.danvega.springevals.cli.ClaudeCli;
import dev.danvega.springevals.cli.CodexCli;
import dev.danvega.springevals.cli.GeminiCli;
import dev.danvega.springevals.cli.QwenCodeCli;
import dev.danvega.springevals.cli.Transcript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Transcript summaries count tool use; they never copy content and never fail on unknown shapes. */
class TranscriptTest {

    private static final String CLAUDE_STREAM = """
            {"type":"system","subtype":"init","cwd":"/workspace","tools":["Bash","Write"]}
            {"type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"Looking."},{"type":"tool_use","id":"t1","name":"Bash","input":{"command":"./mvnw -q compile && curl -s https://repo1.maven.org/maven2/org/x/maven-metadata.xml"}}]}}
            {"type":"user","message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"t1","content":"ok"}]}}
            {"type":"assistant","message":{"role":"assistant","content":[{"type":"tool_use","id":"t2","name":"Write","input":{"file_path":"/workspace/src/main/java/A.java","content":"class A {}"}}]}}
            {"type":"assistant","message":{"role":"assistant","content":[{"type":"tool_use","id":"t3","name":"WebFetch","input":{"url":"https://docs.spring.io/spring-boot/reference/","prompt":"read"}}]}}
            {"type":"assistant","message":{"role":"assistant","content":[{"type":"tool_use","id":"t4","name":"Edit","input":{"file_path":"/workspace/pom.xml","old_string":"a","new_string":"b"}}]}}
            {"type":"result","subtype":"success","result":"Done, all four endpoints work.","total_cost_usd":0.42,"usage":{"input_tokens":12,"output_tokens":34}}
            """;

    @Test
    void claudeStreamIsSummarizedAndItsResultEventParsed() {
        ClaudeCli cli = new ClaudeCli();
        Transcript summary = cli.summarize(CLAUDE_STREAM);
        assertEquals(1, summary.commands());
        assertEquals(2, summary.filesWritten());
        assertEquals(2, summary.urlsFetched());
        assertEquals(List.of("repo1.maven.org", "docs.spring.io"), summary.hosts());

        var parsed = cli.parse(CLAUDE_STREAM, 0);
        assertEquals("Done, all four endpoints work.", parsed.responseText());
        assertEquals(0.42, parsed.costUsd());
        assertEquals(12L, parsed.inputTokens());
        assertEquals(34L, parsed.outputTokens());
    }

    @Test
    void claudePlainJsonStillParses() {
        var parsed = new ClaudeCli().parse("npm warn something\n{\"result\":\"ok\",\"total_cost_usd\":0.1,"
                + "\"usage\":{\"input_tokens\":1,\"output_tokens\":2}}", 0);
        assertEquals("ok", parsed.responseText());
        assertEquals(0.1, parsed.costUsd());
    }

    @Test
    void codexEventsAreSummarizedAndParsed() {
        String stream = """
                {"type":"thread.started","thread_id":"x"}
                {"type":"item.started","item":{"id":"i0","type":"command_execution","command":"bash -lc ./mvnw test","status":"in_progress"}}
                {"type":"item.completed","item":{"id":"i0","type":"command_execution","command":"bash -lc ./mvnw test","exit_code":0,"status":"completed"}}
                {"type":"item.completed","item":{"id":"i1","type":"file_change","changes":[{"path":"pom.xml","kind":"update"},{"path":"src/main/java/B.java","kind":"add"}],"status":"completed"}}
                {"type":"item.completed","item":{"id":"i2","type":"web_search","query":"spring boot 4 webmvc starter"}}
                {"type":"item.completed","item":{"id":"i3","type":"agent_message","text":"Migrated the client."}}
                {"type":"turn.completed","usage":{"input_tokens":100,"cached_input_tokens":50,"output_tokens":7}}
                """;
        CodexCli cli = new CodexCli();
        Transcript summary = cli.summarize(stream);
        assertEquals(1, summary.commands());
        assertEquals(2, summary.filesWritten());
        assertEquals(1, summary.urlsFetched());
        var parsed = cli.parse(stream, 0);
        assertEquals("Migrated the client.", parsed.responseText());
        assertNull(parsed.costUsd());
        assertEquals(100L, parsed.inputTokens());
        assertEquals(7L, parsed.outputTokens());
    }

    @Test
    void qwenEmitsTheClaudeShapeWithItsOwnToolNames() {
        String stream = """
                Warning: running headless with --yolo and no sandbox.
                {"type":"system","subtype":"init","cwd":"/tmp","tools":["run_shell_command","write_file"],"model":"m","permission_mode":"yolo"}
                {"type":"assistant","message":{"role":"assistant","content":[{"type":"tool_use","id":"a","name":"run_shell_command","input":{"command":"./mvnw -q test"}}]}}
                {"type":"assistant","message":{"role":"assistant","content":[{"type":"tool_use","id":"b","name":"write_file","input":{"file_path":"/tmp/x","content":"y"}}]}}
                {"type":"assistant","message":{"role":"assistant","content":[{"type":"tool_use","id":"c","name":"web_fetch","input":{"prompt":"summarize https://docs.spring.io/spring-boot/"}}]}}
                {"type":"result","subtype":"success","result":"All set.","usage":{"input_tokens":5,"output_tokens":6}}
                """;
        var cli = new QwenCodeCli();
        Transcript summary = cli.summarize(stream);
        assertEquals(new Transcript(1, 1, 1, List.of("docs.spring.io")), summary);
        var parsed = cli.parse(stream, 0);
        assertEquals("All set.", parsed.responseText());
        assertEquals(5L, parsed.inputTokens());
        assertEquals(6L, parsed.outputTokens());
        assertNull(parsed.costUsd());
    }

    @Test
    void geminiFlatShapeIsAlsoUnderstood() {
        String stream = """
                {"type":"init","session_id":"s","model":"gemini-x"}
                {"type":"tool_use","tool_name":"run_shell_command","tool_id":"t","parameters":{"command":"curl https://start.spring.io/metadata/client"}}
                {"type":"tool_result","tool_id":"t","status":"success"}
                {"type":"tool_use","tool_name":"write_file","tool_id":"u","parameters":{"file_path":"pom.xml"}}
                {"type":"message","role":"assistant","content":"Migrated."}
                {"type":"result","status":"success","stats":{"models":{"gemini-x":{"tokens":{"prompt":40,"candidates":9}}}}}
                """;
        var cli = new GeminiCli();
        assertEquals(new Transcript(1, 1, 1, List.of("start.spring.io")), cli.summarize(stream));
        var parsed = cli.parse(stream, 0);
        assertEquals("Migrated.", parsed.responseText());
        assertEquals(40L, parsed.inputTokens());
        assertEquals(9L, parsed.outputTokens());
    }

    @Test
    void unknownShapesCountNothing() {
        assertEquals(Transcript.empty(), new ClaudeCli().summarize("plain text, no events"));
        assertEquals(Transcript.empty(), new CodexCli().summarize(null));
        assertEquals("plain text", new CodexCli().parse("plain text", 1).responseText());
    }

    @Test
    void hostsComeFromUrlsWithTrailingPunctuationDropped() {
        assertEquals("start.spring.io", Transcript.hostOf("https://start.spring.io/metadata,"));
        assertNull(Transcript.hostOf("not a url"));
    }
}
