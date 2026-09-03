package dev.danvega.springevals;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.danvega.springevals.Agents.AgentSpec;
import dev.danvega.springevals.cli.HostProbe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The wizard reads doctor output as JSON; the shape is pinned here. */
class AgentDoctorJsonTest {

    @Test
    void jsonCarriesStatusEnabledBillingEstimateAndFindings() {
        FakeHost host = new FakeHost();
        host.environment.put("TOKEN", "sk-ant-oat-test");
        AgentSpec ready = new AgentSpec("ready-agent", "claude", "model",
                Map.of("CLAUDE_CODE_OAUTH_TOKEN", "${TOKEN}"), 0.5);
        AgentSpec blocked = new AgentSpec("blocked-agent", "claude", "model",
                Map.of("CLAUDE_CODE_OAUTH_TOKEN", "${MISSING}"), null);

        Map<String, Object> json = new AgentDoctor(host).json(List.of(ready, blocked), Set.of("blocked-agent"));

        List<?> agents = (List<?>) json.get("agents");
        assertEquals(2, agents.size());
        Map<?, ?> first = (Map<?, ?>) agents.get(0);
        assertEquals("ready-agent", first.get("name"));
        assertEquals("claude", first.get("provider"));
        assertEquals("READY", first.get("status"));
        assertEquals(true, first.get("enabled"));
        assertEquals(0.5, first.get("estimate"));
        assertTrue(String.valueOf(first.get("billing")).contains("subscription token"));
        List<?> findings = (List<?>) first.get("findings");
        assertTrue(findings.stream().allMatch(f -> ((Map<?, ?>) f).containsKey("level")
                && ((Map<?, ?>) f).containsKey("text")));

        Map<?, ?> second = (Map<?, ?>) agents.get(1);
        assertEquals("BLOCKED", second.get("status"));
        assertEquals(false, second.get("enabled"));
        assertNull(second.get("estimate"));
        assertTrue(((List<?>) second.get("findings")).stream()
                .anyMatch(f -> String.valueOf(((Map<?, ?>) f).get("text")).contains("MISSING")));

        Map<?, ?> summary = (Map<?, ?>) json.get("summary");
        assertEquals(1, summary.get("ready"));
        assertEquals(1, summary.get("blocked"));
        String emitted = DashboardJson.write(json);
        assertTrue(emitted.contains("\"status\" : \"READY\""));
        assertFalse(emitted.contains("sk-ant-oat-test"), "credential values must never reach the JSON");
    }

    private static final class FakeHost implements HostProbe {
        final Map<String, String> environment = new HashMap<>();

        @Override
        public String environment(String name) {
            return environment.get(name);
        }

        @Override
        public Path home() {
            return Path.of("/fake-home");
        }

        @Override
        public boolean fileExists(Path path) {
            return false;
        }

        @Override
        public String fileContent(Path path) {
            return null;
        }

        @Override
        public ProbeResult probeLocalModels(String baseUrl, String apiKey, String model) {
            return new ProbeResult(false, false, "unreachable");
        }
    }
}
