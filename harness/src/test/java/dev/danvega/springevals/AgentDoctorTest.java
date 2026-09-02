package dev.danvega.springevals;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.danvega.springevals.Agents.AgentSpec;
import dev.danvega.springevals.cli.Finding.Level;
import dev.danvega.springevals.cli.HostProbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Driven entirely through the HostProbe fake. Readiness is judged from what
 * reaches the container: the expanded agent config env and seeded files.
 */
class AgentDoctorTest {

    private static final Path HOME = Path.of("/fake-home");

    @Test
    void claudeIsReadyWhenTheConfigEnvCarriesTheSubscriptionToken() {
        FakeHost host = new FakeHost();
        host.environment.put("CLAUDE_BENCH_OAUTH_TOKEN", "sk-ant-oat-test");
        AgentSpec spec = new AgentSpec("claude-test", "claude", "model",
                Map.of("CLAUDE_CODE_OAUTH_TOKEN", "${CLAUDE_BENCH_OAUTH_TOKEN}"), 0.5);

        var report = new AgentDoctor(host).inspect(spec);

        assertEquals(Level.READY, report.level());
        assertTrue(hasFinding(report, "billing: subscription token"));
        assertTrue(hasFinding(report, "CLI in the benchmark image: @anthropic-ai/claude-code@"));
    }

    @Test
    void hostLoginAndHostKeysNeverCountBecauseTheContainerSeesOnlyTheConfigEnv() {
        FakeHost host = new FakeHost();
        host.environment.put("ANTHROPIC_API_KEY", "sk-host");
        host.environment.put("CLAUDE_CODE_OAUTH_TOKEN", "sk-ant-oat-host");
        host.files.put(HOME.resolve(".claude/.credentials.json").toString(), true);
        AgentSpec spec = new AgentSpec("claude-test", "claude", "model", Map.of(), 0.5);

        var report = new AgentDoctor(host).inspect(spec);

        assertEquals(Level.BLOCKED, report.level());
        assertTrue(hasFinding(report, "empty Claude config"));
        assertTrue(hasFinding(report, "CLAUDE_CODE_OAUTH_TOKEN"));
    }

    @Test
    void claudeWarnsWhenConfigDeclaresBothSubscriptionTokenAndApiKey() {
        AgentSpec spec = new AgentSpec("claude-test", "claude", "model", Map.of(
                "CLAUDE_CODE_OAUTH_TOKEN", "sk-ant-oat-test",
                "ANTHROPIC_API_KEY", "sk-test"), 0.5);

        var report = new AgentDoctor(new FakeHost()).inspect(spec);

        assertEquals(Level.WARNING, report.level());
        assertTrue(hasFinding(report, "prefers the API key"));
    }

    @Test
    void codexReadsTheCredentialItWillSeedFromTheHostHome() {
        FakeHost host = new FakeHost();
        String authPath = HOME.resolve(".codex/auth.json").toString();
        host.files.put(authPath, true);
        host.fileContents.put(authPath,
                "{\"OPENAI_API_KEY\": null, \"auth_mode\": \"chatgpt\", \"tokens\": {\"access_token\": \"x\"}}");
        AgentSpec spec = new AgentSpec("codex-test", "codex", "model", Map.of(), 0.5);

        var report = new AgentDoctor(host).inspect(spec);

        assertTrue(hasFinding(report, "billing: ChatGPT sign-in seeded from ~/.codex/auth.json"));
        assertFalse(hasFinding(report, "BLOCKED"));
    }

    @Test
    void codexIsBlockedWithoutACredentialToSeed() {
        AgentSpec spec = new AgentSpec("codex-test", "codex", "model", Map.of(), 0.5);

        var report = new AgentDoctor(new FakeHost()).inspect(spec);

        assertEquals(Level.BLOCKED, report.level());
        assertTrue(hasFinding(report, "no ~/.codex/auth.json to seed"));
    }

    @Test
    void codexHostConfigTomlNeverInfluencesBillingBecauseItIsNotSeeded() {
        FakeHost host = new FakeHost();
        String authPath = HOME.resolve(".codex/auth.json").toString();
        host.files.put(authPath, true);
        host.fileContents.put(authPath,
                "{\"OPENAI_API_KEY\": \"sk-file\", \"tokens\": {\"access_token\": \"x\"}}");
        host.fileContents.put(HOME.resolve(".codex/config.toml").toString(), "preferred_auth_method = \"chatgpt\"\n");
        AgentSpec spec = new AgentSpec("codex-test", "codex", "model", Map.of(), 0.5);

        var report = new AgentDoctor(host).inspect(spec);

        assertTrue(hasFinding(report, "preferred_auth_method does not apply in the container"));
    }

    @Test
    void geminiNeedsAnApiKeyInTheConfigEnvBecauseTheImageHasNoGoogleLogin() {
        FakeHost host = new FakeHost();
        host.environment.put("GEMINI_API_KEY", "host-key");
        host.files.put(HOME.resolve(".gemini/oauth_creds.json").toString(), true);
        AgentSpec withoutConfigKey = new AgentSpec("gemini-test", "gemini", "model", Map.of(), 0.1);
        AgentSpec withConfigKey = new AgentSpec("gemini-test", "gemini", "model",
                Map.of("GEMINI_API_KEY", "${GEMINI_API_KEY}"), 0.1);

        assertEquals(Level.BLOCKED, new AgentDoctor(host).inspect(withoutConfigKey).level());
        assertEquals(Level.READY, new AgentDoctor(host).inspect(withConfigKey).level());
    }

    @Test
    void blocksMissingReferencedSecretWithoutExposingAValue() {
        AgentSpec spec = new AgentSpec("kimi", "claude", "model", Map.of(
                "ANTHROPIC_BASE_URL", "https://example.test/anthropic",
                "ANTHROPIC_API_KEY", "${MISSING_KEY}"), 0.3);

        var report = new AgentDoctor(new FakeHost()).inspect(spec);

        assertEquals(Level.BLOCKED, report.level());
        assertTrue(hasFinding(report, "MISSING_KEY"));
    }

    @Test
    void customEndpointCredentialIsCheckedGenerically() {
        FakeHost host = new FakeHost();
        host.environment.put("XAI_API_KEY", "xai-test");
        AgentSpec spec = new AgentSpec("grok", "qwen-code", "grok-x", Map.of(
                "OPENAI_BASE_URL", "https://api.x.ai/v1",
                "OPENAI_API_KEY", "${XAI_API_KEY}",
                "OPENAI_MODEL", "grok-x"), 0.6);

        var report = new AgentDoctor(host).inspect(spec);

        assertEquals(Level.READY, report.level());
        assertTrue(hasFinding(report, "custom endpoint credential is configured"));
    }

    @Test
    void localhostEndpointIsBlockedBecauseInsideTheContainerItIsTheContainer() {
        AgentSpec spec = new AgentSpec("local", "qwen-code", "local-model", Map.of(
                "OPENAI_BASE_URL", "http://localhost:11434/v1",
                "OPENAI_API_KEY", "ollama",
                "OPENAI_MODEL", "local-model"), 0.0);

        var report = new AgentDoctor(new FakeHost()).inspect(spec);

        assertEquals(Level.BLOCKED, report.level());
        assertTrue(hasFinding(report, "host.docker.internal"));
    }

    @Test
    void hostDockerInternalEndpointIsProbedFromTheHostSide() {
        FakeHost host = new FakeHost();
        host.probe = new HostProbe.ProbeResult(true, true,
                "local endpoint is reachable and model is available: local-model");
        AgentSpec spec = new AgentSpec("local", "qwen-code", "local-model", Map.of(
                "OPENAI_BASE_URL", "http://host.docker.internal:11434/v1",
                "OPENAI_API_KEY", "ollama",
                "OPENAI_MODEL", "local-model"), 0.0);

        var report = new AgentDoctor(host).inspect(spec);

        assertEquals(Level.READY, report.level());
        assertEquals("http://localhost:11434/v1", host.probedUrl);
    }

    @Test
    void unknownProviderIsBlockedWithTheSupportedList() {
        AgentSpec spec = new AgentSpec("x", "cursor", "model", Map.of(), 0.1);

        var report = new AgentDoctor(new FakeHost()).inspect(spec);

        assertEquals(Level.BLOCKED, report.level());
        assertTrue(hasFinding(report, "unknown provider 'cursor'"));
    }

    private static boolean hasFinding(AgentDoctor.Report report, String fragment) {
        return report.findings().stream().anyMatch(f -> f.message().contains(fragment));
    }

    private static final class FakeHost implements HostProbe {
        final Map<String, String> environment = new HashMap<>();
        final Map<String, Boolean> files = new HashMap<>();
        final Map<String, String> fileContents = new HashMap<>();
        ProbeResult probe = new ProbeResult(false, false, "unreachable");
        String probedUrl;

        @Override
        public String environment(String name) {
            return environment.get(name);
        }

        @Override
        public Path home() {
            return HOME;
        }

        @Override
        public boolean fileExists(Path path) {
            return files.getOrDefault(path.toString(), false);
        }

        @Override
        public String fileContent(Path path) {
            return fileContents.get(path.toString());
        }

        @Override
        public ProbeResult probeLocalModels(String baseUrl, String apiKey, String model) {
            probedUrl = baseUrl;
            return probe;
        }
    }
}
