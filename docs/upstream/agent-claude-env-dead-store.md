# Upstream issue draft: agent-claude drops environmentVariables and settingSources

**Target repo:** https://github.com/spring-ai-community/agent-client
**Status:** draft, not yet filed. The repo owner posts this himself.

---

## Suggested title

`ClaudeAgentOptions.environmentVariables and settingSources are silently ignored (never forwarded to CLIOptions)`

## Environment

- agent-client 0.16.0 (`org.springaicommunity.agents:agent-claude:0.16.0`)
- claude-code-sdk 1.0.0 (`org.springaicommunity:claude-code-sdk:1.0.0`)
- Claude Code CLI 2.1.221
- macOS (Apple Silicon), JDK 25

## Summary

`ClaudeAgentOptions` exposes `environmentVariables` (a `Map<String, String>`) and `settingSources` (a `List<SettingSource>`). Both are accepted by the builder and stored on the options object. Neither is ever used. `ClaudeAgentModel.buildCLIOptions` never forwards them to the `CLIOptions` it hands to the CLI transport. Both options are dead stores.

The result is silent. No warning, no error. The CLI process spawns with the parent environment and default setting sources, no matter what the caller configured.

## Evidence (bytecode inspection)

We disassembled the published 0.16.0 jar with `javap -c` after observing that the options had no effect at runtime.

`ClaudeAgentModel.buildCLIOptions(AgentTaskRequest)` calls exactly these `CLIOptions.Builder` methods:

```
model, systemPrompt, appendSystemPrompt, timeout,
allowedTools, disallowedTools, permissionMode,
maxTokens, maxThinkingTokens, jsonSchema, mcpServers,
maxTurns, maxBudgetUsd, fallbackModel, build
```

It never calls `CLIOptions.Builder.env(Map)`, `env(String, String)`, or `settingSources(List)`. All three exist on the builder in claude-code-sdk 1.0.0, so the plumbing is available one call away. Meanwhile `ClaudeAgentOptions` carries both fields with working getters and setters:

```
private java.util.Map<java.lang.String, java.lang.String> environmentVariables;
private java.util.List<org.springaicommunity.agents.claude.SettingSource> settingSources;
```

To reproduce the inspection:

```bash
javap -p -c -cp agent-claude-0.16.0.jar \
  org.springaicommunity.agents.claude.ClaudeAgentModel \
  | grep 'CLIOptions$Builder'
```

No `env` or `settingSources` invocation appears in the output.

## Minimal reproduction sketch

```java
ClaudeAgentOptions options = ClaudeAgentOptions.builder()
    .model("claude-sonnet-5")
    .environmentVariables(Map.of("PROBE_VAR", "expected-value"))
    .build();

ClaudeAgentModel model = ClaudeAgentModel.builder()
    .defaultOptions(options)
    .build();

// Task: "Run `printenv PROBE_VAR` and report the output."
AgentResponse response = model.call(taskRequest);
```

Expected: the child CLI process sees `PROBE_VAR=expected-value`.
Actual: the variable is absent. The child inherits only the parent process environment.

Any variable works as the probe. The two that bit us were `CLAUDE_CONFIG_DIR` and `ANTHROPIC_BASE_URL`.

## Observed impact

We hit this in a benchmarking harness (Spring Evals) that drives coding agents through agent-client. Two features depended on the option:

1. **Isolation.** We set `CLAUDE_CONFIG_DIR` to a fresh sterile directory per run so the benchmark measures the model, not the host machine's Claude config, skills, and login. The option was accepted, the sterile directories stayed empty on disk, and the CLI ran with the host config for a full benchmark run before we caught it. Every result recorded before we enforced the environment ourselves had to be voided.
2. **Custom endpoint routing.** We route the Claude CLI to Moonshot's Anthropic-compatible endpoint to benchmark Kimi, by setting `ANTHROPIC_BASE_URL` and `ANTHROPIC_AUTH_TOKEN` through `environmentVariables`. The variables never reached the CLI, so every Kimi attempt hit the default endpoint and failed.

The incident narrative lives in our repo at `results/runs/second-light.notes.md` (see the "Correction, added after the run" section).

The failure mode is the bad part. A builder that accepts an option implies the option works. Callers who rely on it for isolation or routing get silent misbehavior, not an error.

## Workaround we adopted

We stopped passing environment through the SDK and mutate the harness process environment itself before each attempt, so spawned CLIs inherit the values: `setenv` through the FFM API plus `java.lang.ProcessEnvironment` reflection (requires `--add-opens java.base/java.lang` and `--enable-native-access`). We also added a self-test that spawns a probe child process and verifies the variables arrive before any paid run starts. This works but it is global to the JVM, so concurrent agents with different environments are off the table.

## Suggested fix

In `ClaudeAgentModel.buildCLIOptions`, forward the options:

```java
if (options.getEnvironmentVariables() != null) {
    builder.env(options.getEnvironmentVariables());
}
if (options.getSettingSources() != null) {
    builder.settingSources(options.getSettingSources().stream()
        .map(SettingSource::getValue) // or however SettingSource maps to the CLI string
        .toList());
}
```

A regression test that sets a probe variable and asserts the child process sees it would keep this from coming back. If forwarding is intentionally deferred, throwing `UnsupportedOperationException` from the setters would at least fail loudly.

Happy to provide the full javap output or test against a snapshot build.
