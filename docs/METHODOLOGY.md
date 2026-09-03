# Benchmark Methodology

Spring Evals separates correctness, framework competency, agent performance, and cost instead of collapsing them into one unexplained score.

## What a row means

The current **agent track** measures a complete configuration: model, coding-agent CLI, CLI system prompt, tools, permissions, time limit, and environment. Results from Claude Code, Codex, Gemini CLI, and Qwen Code are useful representations of developer experience, but they must not be described as pure model comparisons.

The planned **controlled-model track** will hold the agent loop constant. Every model will receive the same tool schema, command policy, context budget, token budget, network policy, retry policy, and stopping rule. Until that track exists, the public UI and reports must use “agent” language.

## Primary metrics

- Every (agent, eval) cell runs n independent samples (`--samples`, default 3). Each sample is a fresh workspace and a fresh session. An earlier pass never short-circuits the cell.
- Pass rate is primary: samples whose hidden tests passed and whose idiom checks held, over all verdict samples. It is the framework-competency number.
- Functional rate is reported beside it: samples whose hidden tests passed, with or without the idiom. The gap between the two is the share of working but non-idiomatic solutions.
- Coverage is mandatory. A row is leaderboard-eligible when every eval in the catalog has at least one verdict sample for that agent. Partial rows remain visible for diagnostics.
- A 95% Wilson interval accompanies the pass rate, computed over all verdict samples. With a small catalog, rankings are exploratory.
- Cost, duration, and tokens are averaged per sample. Cost per pass is reported separately.
- Outcomes distinguish `pass`, `functional_only`, `test_failure`, `compile_failure`, `policy_failure` (integrity violations such as test suppression or pinned fixture edits), `agent_error`, and `judge_error`. Only the last two are excluded from the pass rate, because they are not verdicts about the model.

## Fair execution policy

Published campaigns must record the eval hash, agent-config hash, harness version, immutable model identifier, provider, CLI version, Java version, operating system, architecture, timestamps, sample number, outcome, whether the hidden tests passed, whether the idiom checks held, duration, token usage when available, reported cost, candidate snapshot hash, and the agent's final response. Results with different content identities are different benchmark cohorts.

Paid execution defaults to three samples per cell and requires an explicit authorization flag plus a campaign cost cap. Provider-side limits remain the final billing backstop.

## Measurement versioning

Every result records a measurement version like `0.3.0+11b0497585a6`. Results with the same full string are comparable, and the leaderboard cohort is keyed on it. The prefix is bumped only in harness batches that change measurement behavior, exactly one bump per batch, never for docs, dashboard, or eval-content changes. The suffix is a content hash of the measurement-critical harness files, so any undeclared drift is visible in the record. The full version history, including per-version trust notes, lives in [VERSIONS.md](VERSIONS.md).

## Isolation and judging

Candidate workspaces are created outside the repository so reference solutions and hidden tests are not siblings of the working directory. After an agent exits, the harness:

1. removes candidate-written tests;
2. restores the trusted Maven launcher and wrapper configuration;
3. rejects build configuration that can skip or redirect tests or outputs, and rejects edits to pinned fixture files (either is a `policy_failure`, and nothing else runs);
4. injects hidden tests;
5. runs the behavioral tests and verifies Surefire evidence that every hidden test class executed (success without that evidence is a `policy_failure`); and
6. applies the idiom checks: required and forbidden source patterns, required and forbidden pom patterns, and required runtime artifacts.

Tests and idiom checks are recorded separately. Tests passed and idiom held is `pass`. Tests passed and idiom missed is `functional_only`, the working-but-last-year's-Spring case. Tests failed is `test_failure` with the idiom result still recorded. The idiom is judged only once the build reached the test phase: on a compile failure or a build timeout, `idiomatic` is null and no classpath resolution runs. If the judge cannot resolve the runtime classpath at all (nonzero exit, timeout, or no file written), the sample is a `judge_error`, never an idiom miss; only a successful resolution with a missing artifact counts against the candidate. Source patterns see the candidate's Java and Kotlin sources with comments stripped, so a justification comment naming the old API can never trip a forbidden pattern. Required runtime artifacts are confirmed on the resolved runtime classpath (`dependency:build-classpath -DincludeScope=runtime`) in the judge container, because pom regexes cannot see dependency scope. The judge refuses a candidate pom that configures the dependency plugin, redirects any build directory, or redirects surefire reports, since each could override the resolution or let pre-seeded files survive `clean`; the classpath file lives outside `target/`, is deleted before resolving, and must be freshly written.

Every eval is validated against its reference candidates: `SOLUTION/` and every `ALTERNATIVES/<name>/` must reach `pass`, and every `WORKAROUNDS/<name>/` must reach `functional_only`. The alternatives are the proof that a check accepts every reasonable framework-native solution; the workarounds are the proof that the checks catch the shortcut the eval exists to catch. Neither directory is part of the eval content hash or ever reaches an agent.

This reduces accidental leakage and common test-skipping paths. Pom policy patterns (required and forbidden) are applied to an XML-parsed re-serialization of the pom, with comments dropped as nodes and CDATA coalesced into plain text. A commented-out dependency can neither satisfy a required mechanism nor trip a forbidden one, and fake comment markers spliced through CDATA cannot hide active configuration from the checks.

An eval may additionally pin fixture files the agent must not touch: a `pinned` array of workspace-relative paths in `EVAL/checks.json`. At judge time, before any tests run, each pinned file's bytes in the candidate workspace are compared against the eval's `project/` fixture; any mismatch or deletion fails the sample as a `policy_failure` with reason `pinned fixture file modified: <path>`. The check reads workspace bytes only.

### Execution in containers

Every sample, and every validate gate, runs in Docker. There is no host execution mode. The harness refuses to run or validate when `docker info` fails.

Each sample uses fresh containers from one benchmark image (`harness/docker/Dockerfile`, part of the measurement content hash; JDK, Node, and all four CLIs are version-pinned, the base image is digest-pinned, and the image tag is derived from the Dockerfile's content hash so a stale local image can never serve a changed Dockerfile). The agent CLI runs headless as a non-root user inside an agent container. The judge's `./mvnw clean test` then runs in a separate fresh container from the same image, started only after the agent container is destroyed: the toolchain is identical, but no process the agent left running, nothing it wrote to the container home (such as `~/.m2/settings.xml`), and none of its environment exists while hidden tests are injected and judged. The candidate workspace is the only host directory mounted writable, and a write probe aborts the sample loudly if the container user cannot write it (a Linux uid mismatch would otherwise record fake failures). The host Maven caches are mounted read-only; Maven reads through to them via `maven.repo.local.tail` and writes to a container-local overlay that starts empty in the judge container, so an agent cannot poison the artifacts or the Maven distribution the judge resolves. Network stays on: the open-book policy is unchanged. `host.docker.internal` is mapped to the host so a local model server stays reachable.

Each agent CLI is one `AgentCli` implementation in the harness: its headless command, the host files it seeds into the container (only codex seeds anything, and only its `auth.json`), how its output is parsed, and its doctor checks. The whole `cli/` package is part of the measurement content hash because the headless command decides how the agent runs.

Record fields: the claude CLI reports cost, token usage, and the final response in its headless JSON output, and the harness parses them; the other CLIs expose no cost or token metadata headlessly, so those fields are null and `responseText` is the CLI's combined output. The recorded CLI version and Java version both carry a `docker:` prefix (the Java version is the container JDK, not the host JVM). Per-sample budget caps cannot be passed to a headless CLI; the campaign cost cap works from per-sample estimates plus claude-reported actuals.

**Parallel execution.** `run` schedules one lane per agent CLI (claude, codex, gemini, qwen-code) and executes lanes concurrently, capped by `--parallel` (default 4, maximum 8 concurrent containers). Samples within a lane stay strictly serial because provider rate limits apply per account. Cost reservations against the campaign cap are atomic across lanes, results are appended and saved under a single lock, and the per-(agent, eval) skip and `--force` semantics are identical to a serial run. Note that parallel lanes share the host's CPU, disk, and network, so recorded durations reflect that load.

What free verification proves: `doctor --docker` proves the image builds, containers start and can write the workspace, all four CLIs execute, the cache mounts are visible, and the Maven wrapper runs in-container; `validate` proves the complete judging pipeline inside containers against every eval's baseline, reference solution, alternatives, and workarounds; the four headless CLI invocations are verified against each CLI's `--help` inside the image and accepted end to end when run without credentials (they fail only on authentication). What it cannot prove: a real model completing a task through a container-spawned CLI end to end; that needs a paid run.

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
- **Container (the enforcement point)**: each sample's CLI runs in a fresh container that has no host HOME, no host config files, and no host environment beyond the expanded agent config env. CLAUDE_CONFIG_DIR points at an empty path baked into the image, so host CLAUDE.md, skills, plugins, and MCP servers cannot load. CODEX_HOME points at a container path seeded with only the host's `~/.codex/auth.json`, and only when the provider is codex, so Codex authenticates without its global AGENTS.md or config.toml. Gemini CLI and Qwen Code find no `~/.gemini` or `~/.qwen` at all. Host-installed CLIs are never used; the pinned CLIs in the image are.
- **Credentials**: no interactive login carries into a container, so every credential must be declared in the agent config env as a `${VAR}` reference. Claude-family runs use CLAUDE_CODE_OAUTH_TOKEN from `claude setup-token` (draws on a Claude subscription; recorded costs are plan-equivalent accounting, not billed dollars) or ANTHROPIC_API_KEY (metered API; recorded costs are exact billed spend). The shipped configs use the subscription token. Gemini runs always use an API key; a Google sign-in cannot reach the container.
- **Doctor**: `doctor` judges readiness from exactly what reaches the container, the expanded config env and the seeded files, and reports the effective billing source from those. A host login, a host API key that the config does not reference, or a `preferred_auth_method` in a host config.toml never counts, because none of them are in the container.
- **History**: runs before harness 0.3.0 executed agents on the host, where the agent SDK silently dropped the isolation settings, and their Claude-family results are documented as contaminated in [VERSIONS.md](VERSIONS.md). Host execution was removed entirely in the 0.6.0 batch.

A container has its own network namespace, so nothing an agent boots can touch host ports or host processes. Independent of that, a sample whose workspace hash is unchanged after the agent finishes is recorded as `agent_error` rather than a model failure, because a CLI that fails with a clean exit code would otherwise score a fake 0%.

Residual risk: prompt-level bans (for example the generator ban in the initializr-parity eval) rely on agent compliance and are documented as limitations rather than enforced guarantees.
