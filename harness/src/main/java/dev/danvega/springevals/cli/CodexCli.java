package dev.danvega.springevals.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

import dev.danvega.springevals.Agents.AgentSpec;

public final class CodexCli implements AgentCli {

    static final String CONTAINER_AUTH = "/sandbox/codex-home/auth.json";

    @Override
    public String id() {
        return "codex";
    }

    @Override
    public String binary() {
        return "codex";
    }

    @Override
    public String npmPackage() {
        return "@openai/codex";
    }

    @Override
    public String pinnedVersion() {
        return "0.152.1";
    }

    /** Codex's own sandbox would leave the bind-mounted workspace read-only; the container is the sandbox. */
    @Override
    public List<String> headlessCommand(String prompt, String model) {
        return List.of("codex", "exec", "--skip-git-repo-check",
                "--dangerously-bypass-approvals-and-sandbox", "--json", "-m", model, prompt);
    }

    @Override
    public String transcriptExtension() {
        return "jsonl";
    }

    /** Only the credential is seeded; config.toml and AGENTS.md stay on the host. */
    @Override
    public List<SeedFile> seedFiles(AgentSpec spec, Path hostHome) {
        return List.of(new SeedFile(hostHome.resolve(".codex/auth.json"), CONTAINER_AUTH));
    }

    /** JSONL events: the last agent_message is the response, turn.completed carries usage; cost is never reported. */
    @Override
    public AgentOutput parse(String output, int exitCode) {
        String response = null;
        Long input = null;
        Long produced = null;
        for (JsonNode event : StreamJson.events(output)) {
            JsonNode item = event.path("item");
            if ("agent_message".equals(text(item, "type")) && text(item, "text") != null) {
                response = text(item, "text");
            }
            JsonNode usage = event.path("usage");
            if ("turn.completed".equals(text(event, "type")) && usage.isObject()) {
                input = usage.hasNonNull("input_tokens") ? usage.get("input_tokens").asLong() : input;
                produced = usage.hasNonNull("output_tokens") ? usage.get("output_tokens").asLong() : produced;
            }
        }
        return new AgentOutput(response == null ? output : response, null, input, produced);
    }

    /** Counts completed items: command_execution, file_change (one per changed path), web_search. */
    @Override
    public Transcript summarize(String output) {
        Transcript.Builder summary = new Transcript.Builder();
        for (JsonNode event : StreamJson.events(output)) {
            if (!"item.completed".equals(text(event, "type"))) {
                continue;
            }
            JsonNode item = event.path("item");
            switch (String.valueOf(text(item, "type"))) {
                case "command_execution" -> summary.command(text(item, "command"));
                case "file_change" -> {
                    JsonNode changes = item.path("changes");
                    int count = changes.isArray() ? changes.size() : 1;
                    for (int i = 0; i < count; i++) {
                        summary.fileWritten();
                    }
                }
                case "web_search" -> summary.fetched("https://web-search/");
                default -> {
                }
            }
        }
        return summary.build();
    }

    private static String text(JsonNode node, String field) {
        return StreamJson.text(node, field);
    }

    @Override
    public List<Finding> doctor(AgentSpec spec, Map<String, String> containerEnv, HostProbe host) {
        List<Finding> findings = new ArrayList<>();
        boolean envKey = present(containerEnv.get("OPENAI_API_KEY"));
        Path authFile = host.home().resolve(".codex/auth.json");
        String auth = host.fileExists(authFile) ? host.fileContent(authFile) : null;
        boolean signIn = auth != null && auth.contains("\"tokens\"");
        boolean fileKey = auth != null && Pattern.compile("\"OPENAI_API_KEY\"\\s*:\\s*\"").matcher(auth).find();
        if (auth == null && !envKey) {
            findings.add(Finding.blocked("no ~/.codex/auth.json to seed into the container and no OPENAI_API_KEY "
                    + "in the agent config env; run `codex login` on the host or declare the key"));
            return findings;
        }
        if (envKey) {
            findings.add(Finding.ready("billing: OPENAI_API_KEY from the agent config env (metered API)"));
            if (signIn) {
                findings.add(Finding.warning("~/.codex/auth.json also holds a ChatGPT sign-in; the CLI decides "
                        + "which one bills inside the container"));
            }
        } else if (signIn && fileKey) {
            findings.add(Finding.warning("~/.codex/auth.json holds both a ChatGPT sign-in and an API key; "
                    + "~/.codex/config.toml is never seeded, so preferred_auth_method does not apply in the container"));
        } else if (signIn) {
            findings.add(Finding.ready("billing: ChatGPT sign-in seeded from ~/.codex/auth.json "
                    + "(subscription covers usage)"));
        } else if (fileKey) {
            findings.add(Finding.ready("billing: OpenAI API key in ~/.codex/auth.json (metered API)"));
        } else {
            findings.add(Finding.ready("Codex credential file is present and will be seeded"));
        }
        findings.add(Finding.warning("this Codex CLI exposes no non-generative login-status command; "
                + "credential validity is unverified"));
        return findings;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
