package dev.danvega.springevals.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import dev.danvega.springevals.Agents.AgentSpec;

public final class ClaudeCli implements AgentCli {

    private static final JsonMapper JSON = JsonMapper.builder().build();

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
        return "2.1.221";
    }

    @Override
    public List<String> headlessCommand(String prompt, String model) {
        return List.of("claude", "-p", prompt, "--model", model,
                "--dangerously-skip-permissions", "--output-format", "json");
    }

    /** The JSON object is printed last; leading npm or progress noise is tolerated. */
    @Override
    public AgentOutput parse(String output, int exitCode) {
        if (output == null) {
            return new AgentOutput(null, null, null, null);
        }
        int start = output.indexOf('{');
        if (start < 0) {
            return new AgentOutput(output, null, null, null);
        }
        try {
            JsonNode node = JSON.readTree(output.substring(start));
            if (!node.isObject() || !node.has("result") && !node.has("total_cost_usd")) {
                return new AgentOutput(output, null, null, null);
            }
            JsonNode usage = node.get("usage");
            return new AgentOutput(
                    node.hasNonNull("result") ? node.get("result").asString() : output,
                    node.hasNonNull("total_cost_usd") ? node.get("total_cost_usd").asDouble() : null,
                    usage != null && usage.hasNonNull("input_tokens") ? usage.get("input_tokens").asLong() : null,
                    usage != null && usage.hasNonNull("output_tokens") ? usage.get("output_tokens").asLong() : null);
        } catch (RuntimeException e) {
            return new AgentOutput(output, null, null, null);
        }
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
