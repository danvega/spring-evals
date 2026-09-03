package dev.danvega.springevals.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;

import dev.danvega.springevals.Agents.AgentSpec;

public final class GeminiCli implements AgentCli {

    @Override
    public String id() {
        return "gemini";
    }

    @Override
    public String binary() {
        return "gemini";
    }

    @Override
    public String npmPackage() {
        return "@google/gemini-cli";
    }

    @Override
    public String pinnedVersion() {
        return "0.58.0";
    }

    @Override
    public List<String> headlessCommand(String prompt, String model) {
        // --skip-trust: an untrusted folder would disable tools for a headless run.
        return List.of("gemini", "-m", model, "-y", "--skip-trust", "-o", "stream-json", "-p", prompt);
    }

    @Override
    public String transcriptExtension() {
        return "jsonl";
    }

    @Override
    public AgentOutput parse(String output, int exitCode) {
        return parseStream(output);
    }

    @Override
    public Transcript summarize(String output) {
        return StreamJson.summarize(StreamJson.events(output), QwenCodeCli.TOOLS);
    }

    /**
     * Two stream shapes are tolerated: the Claude-style `result` event with
     * `result` and `usage`, and the flat Gemini style where assistant text
     * arrives as `message` events and the `result` event carries `stats`.
     * Neither CLI reports dollars, and raw stream text is never the response.
     */
    static AgentOutput parseStream(String output) {
        String response = null;
        Long input = null;
        Long produced = null;
        for (JsonNode event : StreamJson.events(output)) {
            String type = StreamJson.text(event, "type");
            if ("message".equals(type) && "assistant".equals(StreamJson.text(event, "role"))
                    && StreamJson.text(event, "content") != null) {
                response = StreamJson.text(event, "content");
            }
            if ("result".equals(type)) {
                String text = StreamJson.text(event, "result");
                if (text == null) {
                    text = StreamJson.text(event, "response");
                }
                if (text != null) {
                    response = text;
                }
                JsonNode usage = event.path("usage");
                if (usage.isObject()) {
                    input = StreamJson.integer(usage, "input_tokens");
                    produced = StreamJson.integer(usage, "output_tokens");
                }
                JsonNode models = event.path("stats").path("models");
                if (models.isObject()) {
                    long prompt = 0;
                    long candidates = 0;
                    for (JsonNode model : models) {
                        Long p = StreamJson.integer(model.path("tokens"), "prompt");
                        Long c = StreamJson.integer(model.path("tokens"), "candidates");
                        prompt += p == null ? 0 : p;
                        candidates += c == null ? 0 : c;
                    }
                    input = prompt;
                    produced = candidates;
                }
            }
        }
        if (response == null) {
            JsonNode single = StreamJson.object(output);
            response = single == null ? null : StreamJson.text(single, "response");
        }
        return new AgentOutput(response, null, input, produced);
    }

    /** The image has no ~/.gemini, so a Google sign-in never applies; only an API key in the config env works. */
    @Override
    public List<Finding> doctor(AgentSpec spec, Map<String, String> containerEnv, HostProbe host) {
        List<Finding> findings = new ArrayList<>();
        boolean gemini = present(containerEnv.get("GEMINI_API_KEY"));
        boolean google = present(containerEnv.get("GOOGLE_API_KEY"));
        if (!gemini && !google) {
            findings.add(Finding.blocked("the container has no Google sign-in; declare GEMINI_API_KEY "
                    + "in the agent config env"));
            return findings;
        }
        findings.add(Finding.ready("billing: Gemini API key (metered or AI Studio free tier)"));
        if (gemini && google) {
            findings.add(Finding.warning("the agent config declares both GEMINI_API_KEY and GOOGLE_API_KEY; "
                    + "the CLI prefers GOOGLE_API_KEY, so declare only the one that should bill"));
        }
        return findings;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
