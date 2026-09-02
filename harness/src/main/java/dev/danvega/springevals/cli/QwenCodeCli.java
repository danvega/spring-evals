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
        return "0.21.5";
    }

    @Override
    public List<String> headlessCommand(String prompt, String model) {
        return List.of("qwen", "-y", "-m", model, "-p", prompt);
    }

    @Override
    public AgentOutput parse(String output, int exitCode) {
        return new AgentOutput(output, null, null, null);
    }

    /** Reached only without OPENAI_BASE_URL; endpoint credentials are checked generically. */
    @Override
    public List<Finding> doctor(AgentSpec spec, Map<String, String> containerEnv, HostProbe host) {
        return List.of(Finding.blocked("qwen-code needs OPENAI_BASE_URL, OPENAI_API_KEY, and OPENAI_MODEL "
                + "in the agent config env"));
    }
}
