# Benchmark Methodology

Spring Evals separates correctness, framework competency, agent performance, and cost instead of collapsing them into one unexplained score.

## What a row means

The current **agent track** measures a complete configuration: model, coding-agent CLI, CLI system prompt, tools, permissions, time limit, and environment. Results from Claude Code, Codex, Gemini CLI, and Qwen Code are useful representations of developer experience, but they must not be described as pure model comparisons.

The planned **controlled-model track** will hold the agent loop constant. Every model will receive the same tool schema, command policy, context budget, token budget, network policy, retry policy, and stopping rule. Until that track exists, the public UI and reports must use “agent” language.

## Primary metrics

- Pass@1 is primary. It reflects whether one fresh session completes the task.
- Pass@k is secondary and always states k. Retries are fresh workspaces and stop after success.
- Coverage is mandatory. Partial campaigns remain visible but are not leaderboard-eligible.
- A 95% Wilson interval accompanies binary pass rates. With nine tasks, rankings are exploratory.
- Cost and duration are aggregated per task across all attempts. Cost per pass is reported separately.
- Failure kinds distinguish agent execution, compilation, behavioral tests, policy checks, and judge infrastructure.

## Fair execution policy

Published campaigns must record the eval hash, agent-config hash, harness version, immutable model identifier, provider, CLI version, Java version, operating system, architecture, timestamps, attempt number, duration, token usage when available, reported cost, candidate snapshot hash, and the agent's final response. Results with different content identities are different benchmark cohorts.

Paid execution defaults to one attempt and requires an explicit authorization flag plus a campaign cost cap. Provider-side limits remain the final billing backstop.

## Measurement versioning

Every result records a measurement version like `0.3.0+11b0497585a6`. Results with the same full string are comparable, and the leaderboard cohort is keyed on it. The prefix is bumped only in harness batches that change measurement behavior, exactly one bump per batch, never for docs, dashboard, or eval-content changes. The suffix is a content hash of the measurement-critical harness files, so any undeclared drift is visible in the record. The full version history, including per-version trust notes, lives in [VERSIONS.md](VERSIONS.md).

## Isolation and judging

Candidate workspaces are created outside the repository so reference solutions and hidden tests are not siblings of the working directory. After an agent exits, the harness:

1. removes candidate-written tests;
2. restores the trusted Maven launcher and wrapper configuration;
3. rejects build configuration that can skip or redirect tests;
4. injects hidden tests;
5. applies trusted deterministic mechanism checks;
6. runs the behavioral tests; and
7. verifies Surefire evidence that every hidden test class executed.

This reduces accidental leakage and common test-skipping paths. Pom policy patterns (required and forbidden) are applied to an XML-parsed re-serialization of the pom, with comments dropped as nodes and CDATA coalesced into plain text. A commented-out dependency can neither satisfy a required mechanism nor trip a forbidden one, and fake comment markers spliced through CDATA cannot hide active configuration from the checks.

### Sandbox modes

Since harness 0.4.0 the harness has two execution modes, selected with `--sandbox docker|host` on `run` and `validate`. The default is docker whenever `docker info` succeeds, host otherwise, and the active mode is printed at the start of every run.

**Docker mode** runs each attempt in fresh containers from one benchmark image (`harness/docker/Dockerfile`, part of the measurement content hash; JDK, Node, and all four CLIs are version-pinned, the base image is digest-pinned, and the image tag is derived from the Dockerfile's content hash so a stale local image can never serve a changed Dockerfile). The agent CLI runs headless as a non-root user inside an agent container. The judge's `./mvnw clean test` then runs in a separate fresh container from the same image, started only after the agent container is destroyed: the toolchain is identical, but no process the agent left running, nothing it wrote to the container home (such as `~/.m2/settings.xml`), and none of its environment exists while hidden tests are injected and judged. The candidate workspace is the only host directory mounted writable, and a write probe aborts the attempt loudly if the container user cannot write it (a Linux uid mismatch would otherwise record fake failures). The host Maven caches are mounted read-only; Maven reads through to them via `maven.repo.local.tail` and writes to a container-local overlay that starts empty in the judge container, so an agent cannot poison the artifacts or the Maven distribution the judge resolves. Network stays on: the open-book policy is unchanged.

Docker-mode record fields: the claude CLI reports cost, token usage, and the final response in its headless JSON output, and the harness parses them; the other CLIs expose no cost or token metadata headlessly, so those fields are null and `responseText` is the CLI's combined output. The recorded CLI version and Java version both carry a `docker:` prefix (the Java version is the container JDK, not the host JVM), so docker- and host-mode records are never treated as the same cached attempt and the mode is visible on every record. Claude's per-attempt `budgetUsd` cap cannot be passed to the headless CLI in docker mode; the campaign cost cap works from per-attempt estimates plus claude-reported actuals, and the run prints a note when a budget-capped config runs in docker mode.

**Host mode** is the fallback and keeps the process-environment isolation described below. It is not full OS isolation.

What free verification proves for docker mode: `doctor --sandbox docker` proves the image builds, containers start and can write the workspace, all four CLIs execute, the cache mounts are visible, and the Maven wrapper runs in-container; `validate --sandbox docker` proves the complete judging pipeline inside containers against every eval's baseline and reference solution; the four headless CLI invocations are verified against each CLI's `--help` inside the image and accepted end to end when run without credentials (they fail only on authentication). What it cannot prove: a real model completing a task through a container-spawned CLI end to end; that needs a paid run.

## Open-book and closed-book tracks

Network access changes the construct being measured:

- **Closed-book** measures learned Spring knowledge and reasoning. Workspace commands have no web access.
- **Open-book** measures realistic agent performance with identical documentation access for every model.

Results from these tracks must never be mixed.

## Holdouts and contamination

Public prompts and solutions eventually become searchable and may enter training data. The production leaderboard should use a private, rotating holdout catalog. Only the starter project and prompt are exposed during execution. Retired holdouts can later be published for audit and community learning.

## Catalog balance

The overall score should not be considered stable until the catalog has enough tasks to balance Spring portfolio, task type, difficulty, and skill dimension. Target dimensions include upgrade/API recall, diagnosis, data access, security, testing, observability, messaging, architecture, and maintainability. Boot 4.0 and Boot 4.1 should be separate versioned cohorts rather than silently replacing one another.

## Host context isolation

A benchmark run is invalid if the agent can see instructions or knowledge the host machine happens to carry: a global CLAUDE.md, user-installed skills, MCP servers, or global agent context files. On a maintainer's machine these can amount to answer keys for the very tasks being measured.

Controls, per layer:

- **Workspace**: candidate workspaces are created outside the repository, and the harness strips agent context files (CLAUDE.md, AGENTS.md, GEMINI.md, QWEN.md, .claude/, .mcp.json, Cursor and Copilot instruction files) from every fresh copy before the agent starts.
- **Container (docker mode, the default when Docker is present)**: each attempt's CLI runs in a fresh container that has no host HOME, no host config files, and no host environment beyond what the agent config declares. CLAUDE_CONFIG_DIR points at an empty path baked into the image. CODEX_HOME points at a container path seeded with only the host's `~/.codex/auth.json`, and only when the provider is codex, so Codex authenticates without its global AGENTS.md or config.toml. Gemini CLI and Qwen Code find no `~/.gemini` or `~/.qwen` at all. This retires the global-context warnings below for docker-mode runs.
- **Process environment (the enforcement point in host mode)**: agent CLIs inherit their environment from the harness process, so the harness mutates its own environment around every attempt (`EnvSandbox`, via libc setenv plus the JDK's cached env view). Per attempt it applies the agent config's env, removes host credentials the config does not re-declare (every ANTHROPIC*/CLAUDE* variable for the Claude family, Google credential variables for Gemini), and for Claude Code sets CLAUDE_CONFIG_DIR to a fresh empty directory so host CLAUDE.md, skills, plugins, and MCP servers cannot load. A self-test runs before any money is spent; if the mechanism is unavailable the run refuses to start.
- **Why the environment is mutated at process level**: the SDK's per-model `environmentVariables` option is silently dropped by agent-claude 0.16.0 (a dead store, verified by bytecode inspection). Runs before harness 0.3 relied on it, so their Claude attempts executed against the host config. This was discovered when a run's sterile config directories stayed empty and an aliased-endpoint agent hit the wrong API. Treat pre-0.3 Claude-family results as potentially contaminated.
- **Subscription note**: no interactive login carries into the sterile config dir, so Claude-family runs need a credential in the agent config env: CLAUDE_CODE_OAUTH_TOKEN from `claude setup-token` (draws on a Claude subscription; recorded costs are plan-equivalent accounting, not billed dollars) or ANTHROPIC_API_KEY (metered API; recorded costs are exact billed spend). The shipped configs use the subscription token.
- **Codex, Gemini CLI, Qwen Code (host mode only)**: in host mode these CLIs read global context files the harness does not redirect (~/.codex/AGENTS.md and config.toml, ~/.gemini/GEMINI.md, ~/.qwen/QWEN.md). The Gemini CLI additionally picks its auth source from ~/.gemini/settings.json (`selectedAuthType`); if that points at a Google account login, the CLI ignores the benchmark's GEMINI_API_KEY, so `doctor` reports which billing source is active. `doctor` warns when global context files exist on the host. Docker mode is the fix: the container has none of these files, which is why it is the default and why published campaigns should use it.

Two adjacent protections ride on the host-mode mechanism: `SERVER_PORT=0` is set for every host-mode attempt so an agent booting a Spring app binds an ephemeral port instead of 8080 and cannot collide with (or kill) whatever the host user has running. Docker mode retires this caveat: a container has its own network namespace, so nothing an agent boots can touch host ports or host processes. Independent of mode, an attempt whose workspace hash is unchanged after the agent finishes is recorded as `agent_error` rather than a model failure, because a CLI that fails with a clean exit code would otherwise score a fake 0%.

Residual risk: prompt-level bans (for example the generator ban in the initializr-parity eval) rely on agent compliance and are documented as limitations rather than enforced guarantees.
