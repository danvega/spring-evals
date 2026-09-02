package dev.danvega.springevals.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        return "0.1.13";
    }

    @Override
    public List<String> headlessCommand(String prompt, String model) {
        return List.of("gemini", "-m", model, "-y", "-p", prompt);
    }

    @Override
    public AgentOutput parse(String output, int exitCode) {
        return new AgentOutput(output, null, null, null);
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
