package dev.danvega.springevals.cli;

import java.util.List;
import java.util.Map;

import dev.danvega.springevals.Agents.AgentSpec;

/** Qwen Code is the generic OpenAI-compatible driver; the endpoint always comes from the agent config env. */
public final class QwenCodeCli implements AgentCli {

    @Override
    public String id() {
        return "qwen-code";
    }

    @Override
    public String binary() {
        return "qwen";
    }

    @Override
    public String npmPackage() {
        return "@qwen-code/qwen-code";
    }

    @Override
    public String pinnedVersion() {
        return "0.22.3";
    }

    @Override
    public List<String> headlessCommand(String prompt, String model) {
        return List.of("qwen", "-y", "-m", model, "-o", "stream-json", "-p", prompt);
    }

    @Override
    public String transcriptExtension() {
        return "jsonl";
    }

    /** Qwen Code emits the Claude Code stream shape (system/init, assistant, result) with its own tool names. */
    static final Map<String, StreamJson.Kind> TOOLS = Map.of(
            "run_shell_command", StreamJson.Kind.COMMAND,
            "write_file", StreamJson.Kind.WRITE, "edit", StreamJson.Kind.WRITE,
            "replace", StreamJson.Kind.WRITE, "notebook_edit", StreamJson.Kind.WRITE,
            "web_fetch", StreamJson.Kind.FETCH);

    @Override
    public AgentOutput parse(String output, int exitCode) {
        return GeminiCli.parseStream(output);
    }

    @Override
    public Transcript summarize(String output) {
        return StreamJson.summarize(StreamJson.events(output), TOOLS);
    }

    /** Reached only without OPENAI_BASE_URL; endpoint credentials are checked generically. */
    @Override
    public List<Finding> doctor(AgentSpec spec, Map<String, String> containerEnv, HostProbe host) {
        return List.of(Finding.blocked("qwen-code needs OPENAI_BASE_URL, OPENAI_API_KEY, and OPENAI_MODEL "
                + "in the agent config env"));
    }
}
