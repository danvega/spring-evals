package dev.danvega.springevals.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;

import dev.danvega.springevals.Agents.AgentSpec;

public final class ClaudeCli implements AgentCli {

    @Override
    public String id() {
        return "claude";
    }

    @Override
    public String binary() {
        return "claude";
    }

    @Override
    public String npmPackage() {
        return "@anthropic-ai/claude-code";
    }

    @Override
    public String pinnedVersion() {
        return "2.1.259";
    }

    @Override
    public List<String> headlessCommand(String prompt, String model) {
        // stream-json needs --verbose; every event lands in the transcript and the terminal result event carries the totals.
        return List.of("claude", "-p", prompt, "--model", model,
                "--dangerously-skip-permissions", "--output-format", "stream-json", "--verbose");
    }

    @Override
    public String transcriptExtension() {
        return "jsonl";
    }

    /** The terminal `result` event is authoritative; leading noise and the plain-json shape are tolerated. */
    @Override
    public AgentOutput parse(String output, int exitCode) {
        if (output == null) {
            return new AgentOutput(null, null, null, null);
        }
        JsonNode node = resultEvent(output);
        if (node == null) {
            return new AgentOutput(output, null, null, null);
        }
        JsonNode usage = node.get("usage");
        // A non-text result (Jackson 3 asString() throws on objects) must never cost the numbers.
        JsonNode result = node.get("result");
        String text = result == null || result.isNull() ? output
                : result.isString() ? result.asString() : result.toString();
        return new AgentOutput(
                text,
                node.hasNonNull("total_cost_usd") ? node.get("total_cost_usd").asDouble() : null,
                usage != null && usage.hasNonNull("input_tokens") ? usage.get("input_tokens").asLong() : null,
                usage != null && usage.hasNonNull("output_tokens") ? usage.get("output_tokens").asLong() : null);
    }

    private static JsonNode resultEvent(String output) {
        JsonNode last = null;
        for (JsonNode event : StreamJson.events(output)) {
            if (event.has("result") || event.has("total_cost_usd")) {
                last = event;
            }
        }
        if (last != null) {
            return last;
        }
        JsonNode node = StreamJson.object(output);
        return node != null && (node.has("result") || node.has("total_cost_usd")) ? node : null;
    }

    static final Map<String, StreamJson.Kind> TOOLS = Map.of(
            "Bash", StreamJson.Kind.COMMAND,
            "Write", StreamJson.Kind.WRITE, "Edit", StreamJson.Kind.WRITE,
            "MultiEdit", StreamJson.Kind.WRITE, "NotebookEdit", StreamJson.Kind.WRITE,
            "WebFetch", StreamJson.Kind.FETCH);

    /** Counts assistant tool_use blocks: Bash commands, file writes, and fetched URLs. */
    @Override
    public Transcript summarize(String output) {
        return StreamJson.summarize(StreamJson.events(output), TOOLS);
    }

    /** CLAUDE_CONFIG_DIR in the image is empty, so a host login never applies; the config env must carry the credential. */
    @Override
    public List<Finding> doctor(AgentSpec spec, Map<String, String> containerEnv, HostProbe host) {
        List<Finding> findings = new ArrayList<>();
        boolean oauthToken = present(containerEnv.get("CLAUDE_CODE_OAUTH_TOKEN"));
        boolean apiKey = present(containerEnv.get("ANTHROPIC_API_KEY"));
        if (oauthToken) {
            findings.add(Finding.ready("billing: subscription token from `claude setup-token` "
                    + "(draws on the Claude plan inside the empty container config)"));
            if (apiKey) {
                findings.add(Finding.warning("the agent config declares both CLAUDE_CODE_OAUTH_TOKEN and "
                        + "ANTHROPIC_API_KEY; the CLI prefers the API key, so billing would be metered API"));
            }
        } else if (apiKey) {
            findings.add(Finding.ready("billing: ANTHROPIC_API_KEY (metered API)"));
        } else {
            findings.add(Finding.blocked("the container starts with an empty Claude config and only the agent "
                    + "config env, so a host login never applies. Declare CLAUDE_CODE_OAUTH_TOKEN "
                    + "(from `claude setup-token`, subscription) or ANTHROPIC_API_KEY (metered API) in the config env"));
        }
        return findings;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
