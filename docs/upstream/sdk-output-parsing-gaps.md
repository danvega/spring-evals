# Upstream issue draft: output parsing gaps lose results the agent already produced

**Target repos:**
- https://github.com/spring-ai-community/agent-client (codex, gemini, qwen-code adapters)
- https://github.com/spring-ai-community/claude-agent-sdk-java (claude-code-sdk `MessageParser`)

**Status:** draft, not yet filed. The repo owner posts this himself. Could be filed as one issue on agent-client with a cross-reference, or split per repo.

---

## Suggested title

`Parse failures on CLI output should degrade to a partial response, not lose the run`

## Environment

- agent-client 0.16.0 (`org.springaicommunity.agents`, modules agent-claude, agent-codex, agent-gemini, agent-qwen-code)
- claude-code-sdk 1.0.0 (`org.springaicommunity:claude-code-sdk:1.0.0`)
- CLI versions: Claude Code 2.1.221, codex-cli 0.146.0, gemini 0.1.13, qwen-code 0.21.5
- macOS (Apple Silicon), JDK 25

## Context

We run a benchmarking harness (Spring Evals) that drives 12 CLI/model pairs through `AgentClient`. A recent batch surfaced two related parsing problems. One is cosmetic. One loses real results. Both come from the same root: the parsers treat unexpected CLI output as fatal or as an error, when the CLI contract is clearly evolving under them.

Run notes: `results/runs/eager-boot-85.notes.md` in our repo.

## Problem A: claude-code-sdk logs ERROR for the CLI's tool_progress heartbeat

Claude Code CLI 2.1.221 emits a `tool_progress` message type on the stream-json output. claude-code-sdk 1.0.0's `MessageParser` does not recognize it and logs this at ERROR level (string taken from the published jar):

```
Unrecognized message type '{}' — skipping. This may indicate the Claude CLI has added a new message type. Raw JSON: {}
```

A raw line captured verbatim from our run console (CLI 2.1.221). Long-running tool calls emit one every 30 seconds:

```json
{
  "type": "tool_progress",
  "tool_use_id": "toolu_01KRTz5yoXsuappGz1NQQibE-heartbeat-0",
  "tool_name": "Bash",
  "parent_tool_use_id": "toolu_01KRTz5yoXsuappGz1NQQibE",
  "elapsed_time_seconds": 30,
  "heartbeat": true,
  "session_id": "297d32e9-4813-4fa6-a515-641b69705d1f",
  "uuid": "5ce855a1-293a-4e8d-ad21-527471b7e11c"
}
```

The parser already does the right thing functionally: it skips the message and continues. The problem is only severity and volume. A benchmark attempt with long Maven builds produces dozens of these, every one an ERROR line, all noise. Anyone alerting on ERROR logs gets paged for a heartbeat.

Suggested handling, either is fine:

- Model `tool_progress` as a known message type (it is useful progress signal for UIs), or
- Downgrade the unrecognized-type log to DEBUG or a once-per-type WARN. An unknown type that is safely skipped is not an error by the parser's own logic.

## Problem B: codex/gemini/qwen-code adapters throw after the CLI has already succeeded

On every codex, gemini, and qwen-code attempt in the batch, `AgentClient.run()` failed with:

```
NullPointerException: Cannot invoke "java.util.function.Supplier.get()" because "defaultObjectSupplier" is null
```

The exception fires while parsing the CLI's output, after the CLI process has finished its work. The workspaces show completed, correct projects. In several cases the agent's work would have passed our hidden test suite. But because the exception propagates out of `run()`, the caller loses everything the adapter was supposed to deliver: the response text, the token counts, and the cost. Our leaderboard shows `n/a` in the cost column for all three CLI families for this reason.

The order of events matters:

1. CLI runs, agent does the work, CLI exits.
2. Adapter parses the CLI output and hits the NPE.
3. `run()` throws. The caller gets an exception instead of a response.
4. The workspace on disk contains the successful result nobody was told about.

Our harness mostly survives this because judging is workspace-based, but two gemini attempts still had to be recorded as no-verdict errors because of the exception. Any caller that consumes the `AgentResponse` directly gets a hard failure for a run that succeeded.

## The ask

Parse errors on agent output should degrade to a partial response object rather than an exception, once the CLI process itself has completed. Something like:

- Return an `AgentResponse` with whatever was parsed successfully, plus a structured parse-failure flag (or a metadata entry) the caller can inspect.
- Reserve exceptions for the cases where there is nothing to return: the CLI failed to launch or exited before producing output.

The agent's work exists on disk either way. The response object should not pretend it does not.

Problem A is the same principle applied to a single message: unknown output should cost at most a log line, never the run.

Happy to share full logs from the batch or re-run against a snapshot build.
