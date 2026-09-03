# Spring Evals

Evals for Spring to test how well AI models and coding agents write real Spring code.

**New here?** The [getting started guide](docs/GETTING_STARTED.md) takes you from clone to your first scored run in about ten minutes, one agent, a dollar or two. The rest of this README is the full reference.

Modern models score well on generic coding benchmarks. They do much worse on framework-specific work, especially anything released after their training data. Spring Boot 4 and Spring Framework 7 shipped breaking changes (Jackson 3, modular auto-configuration, new HTTP clients, new testing tools) that trip up even frontier models. This project measures that gap, per agent and per model, with tasks a real Spring developer would recognize.

Complementary to [Agent Bench](https://github.com/markpollack/agent-bench), which benchmarks agents on enterprise workflows. This project benchmarks framework competency: does the model actually know Spring? See [Built on](#built-on) for the projects underneath this one.

## What it produces

A leaderboard, a per-project heatmap, and a log for every run. `./spring-evals report` rebuilds all of them from `results/results.json`.

Here is the honest state of the numbers. Harness 0.6.0 reworked the judge and the scoring (the details are in [docs/VERSIONS.md](docs/VERSIONS.md)), and the leaderboard cohort is keyed on the harness version. So the 0.6.0 cohort is empty until the first campaign runs under it. The last real run is [lazy-cache-39](results/runs/lazy-cache-39.md) from 2026-08-06: the Claude family on the six Boot evals, one sample each, 24 samples, 17 passed, under harness 0.5.0 and the old judge. One of its six evals, boot/003, was later found to have been judged by a defective check, so read that run as five evals. It stays in run history and in the dashboard's run drill-down. It is not on the leaderboard.

The screenshots below are from an earlier run in August 2026, not from lazy-cache-39. The catalog held ten evals then, and they show the old leaderboard columns. The current dashboard shows the pass rate with its 95% interval and the functional rate beside it.

![Leaderboard from a pre-0.6.0 run: the old per-agent pass columns with confidence intervals, tokens, and cost](docs/images/dashboard-leaderboard.png)

Every run is recorded by name with its own scoreboard and a findings write-up, so infrastructure failures are never mistaken for model failures:

![Run detail from a pre-0.6.0 run: findings narrative and per-agent scoreboard](docs/images/dashboard-run-detail.png)

Coverage across the Spring portfolio, with room to grow into all 22 project suites:

![Per-project heatmap from a pre-0.6.0 run](docs/images/dashboard-projects.png)

There is also a terminal-style dark mode:

![Dark mode dashboard from a pre-0.6.0 run](docs/images/dashboard-dark.png)

## How it works

Evals are organized into suites, one per project on [spring.io/projects](https://spring.io/projects). Every portfolio project has a suite directory under [evals/](evals), from the big ones (`framework`, `boot`, `data`, `security`, `cloud`, `ai`) to the full long tail (`batch`, `integration`, `kafka`, `amqp`, `graphql`, `grpc`, `modulith`, `session`, `authorization-server`, `hateoas`, `rest-docs`, `ldap`, `pulsar`, `shell`, `web-flow`, `web-services`). Five suites have evals today; the other seventeen are empty. Each suite README says what belongs there and links to the proposal form.

Each eval id is `<project>/<nnn>-<name>`, for example `boot/003-jackson3-migration`. The leaderboard reports an overall score plus a per-project breakdown, so you can see that a model handles Boot upgrades well but falls over on Spring AI.

Each eval is a self-contained Spring Boot project plus a task:

- `PROMPT.md` describes the task the way a developer would. Symptoms only, never the solution.
- `project/` is the workspace the agent gets. It may contain broken code (fix tasks) or a skeleton (build tasks).
- `EVAL/` holds hidden JUnit tests and an optional `checks.json` of idiom checks and pinned fixtures. The agent never sees them.
- `SOLUTION/` is the reference solution. `ALTERNATIVES/` holds other legitimate solutions, and `WORKAROUNDS/` holds shortcuts that pass the tests but miss the idiom. `validate` requires the solution and every alternative to reach `pass`, and every workaround to reach `functional_only`. None of these directories ever reach an agent.

The harness is a plain Java 26 app in `harness/` with no framework dependencies. Each agent CLI is one `AgentCli` implementation. For each sample the harness copies `project/` to a fresh workspace outside the repository and runs the coding-agent CLI headless in a fresh container from one pinned image. The CLI's own session stream is kept outside the repository and scanned for contamination. After the agent container is destroyed, the harness removes candidate tests, restores the trusted Maven launcher, refuses symbolic links and build tricks, injects the hidden tests, and runs the judge in a second fresh container. The judge verifies that every hidden test actually executed, then applies the idiom checks. Every (agent, eval) cell runs three independent samples by default; `--samples` changes that.

Metrics, as defined in [Benchmark methodology](docs/METHODOLOGY.md):

- **Pass rate**: samples whose hidden tests passed and whose idiom checks held, over all verdict samples. The primary capability metric.
- **Functional rate**: samples whose hidden tests passed, with or without the idiom. The gap to the pass rate is the share of working but last-generation solutions.
- **95% Wilson interval** on the pass rate, computed over all verdict samples. With a small catalog, rankings are exploratory.
- **Coverage**: a row is leaderboard-eligible only when every eval in the catalog has at least one verdict sample for that agent. Partial rows stay visible for diagnostics.
- **Avg duration**, **avg cost**, and **avg tokens** per sample, plus **cost per pass**.
- **Outcomes**: `pass`, `functional_only`, `test_failure`, `compile_failure`, `policy_failure`, `agent_error`, and `judge_error`. Only the last two are excluded from the pass rate, because they are not verdicts about the model.
- **Contamination flags**: samples whose transcript referenced the benchmark repository, the eval, or a hidden directory. The verdict is kept and the flag is shown; exclusion is a human call.

Rows measure an **agent configuration**: model plus coding-agent CLI, tool policy, and runtime. They are not pure model measurements. See [Benchmark methodology](docs/METHODOLOGY.md) for the controlled-model track design.

## Quick start

Requirements: JDK 26+, Docker, and network access for Maven (SDKMAN users: `sdk env` activates the pinned JDK from `.sdkmanrc`). Every agent and every judge runs in a container, so Docker must be running even for `validate`. Scored runs also need credentials; see [Setup for real runs](#setup-for-real-runs) and read the [cost warning](#cost-warning) first.

The fastest path is the onboarding wizard: run `./spring-evals serve` and open http://localhost:4173/onboarding.html. It walks the same steps as the commands below and ends by printing your run command. The text version:

```bash
# see available evals
./spring-evals list

# verify credentials, billing sources, env references, and endpoints as the container sees them
# no prompt is sent and no generation request is made
./spring-evals doctor
./spring-evals doctor --family codex
./spring-evals doctor --agent claude-sonnet-5,gemini-3-1-pro
./spring-evals doctor --docker      # also build the image and probe every CLI inside it
./spring-evals doctor --json        # the same report as one JSON document

# check every eval is well-formed: broken fails, solution and alternatives pass, workarounds are functional only
./spring-evals validate
./spring-evals validate boot/002-restclient-migration

# project the cost of the three-eval pilot (no model calls)
./spring-evals estimate --agent claude-sonnet-5 --pilot --samples 1

# paid execution is locked until both intent and a campaign cap are explicit
./spring-evals run --agent claude-sonnet-5 --pilot --samples 1 \
  --allow-paid-run --max-total-cost 2.00

# scope to one project's suite, or one eval
./spring-evals estimate --agent claude-sonnet-5 --project boot
./spring-evals estimate --agent claude-sonnet-5 --eval boot/003-jackson3-migration

# rebuild the leaderboard and dashboard data, then serve the dashboard and the wizard
./spring-evals report
./spring-evals serve
```

Results accumulate in `results/results.json`. Cache identity includes the eval content, agent configuration, and harness version, so edited prompts, tests, configs, or judging code cannot silently reuse stale results. Pass `--force` to rerun the current identity.

## Current evals

Sixteen evals across five suites. The suite READMEs (linked in the Project column) describe what each eval measures and how models fail it.

| Eval | Project | Type | Difficulty | What it tests |
|---|---|---|---|---|
| [ai/000-chatclient-basics](evals/ai/000-chatclient-basics) | [spring-ai](evals/ai) | fix | medium | Structured output through the framework against a stub model that answers in prose unless asked for a shape |
| [boot/000-initializr-parity](evals/boot/000-initializr-parity) | [spring-boot](evals/boot) | build | medium | Generating a new Boot 4 project from scratch, judged against the Spring Initializr bar |
| [boot/001-modular-autoconfig](evals/boot/001-modular-autoconfig) | [spring-boot](evals/boot) | fix | medium | Diagnosing two features that silently vanished when Boot 4 split its auto-configuration into modules |
| [boot/002-restclient-migration](evals/boot/002-restclient-migration) | [spring-boot](evals/boot) | fix | easy | Migrating HTTP client code that no longer compiles to Boot 4's current client stack |
| [boot/003-jackson3-migration](evals/boot/003-jackson3-migration) | [spring-boot](evals/boot) | fix | medium | Migrating Jackson 2 code to Jackson 3 while preserving the API's JSON contract |
| [boot/004-flyway-module](evals/boot/004-flyway-module) | [spring-boot](evals/boot) | fix | medium | Diagnosing migrations that silently stopped running on Boot 4, masked by the ORM recreating empty tables |
| [boot/005-h2-console](evals/boot/005-h2-console) | [spring-boot](evals/boot) | fix | easy | Restoring a development console that Boot 4 moved out of the monolithic auto-configuration |
| [data/000-n-plus-one](evals/data/000-n-plus-one) | [spring-data](evals/data) | fix | hard | Recognizing and fixing an N+1 query pattern from a symptom description, verified by query volume |
| [data/001-repository-aot](evals/data/001-repository-aot) | [spring-data](evals/data) | fix | hard | Moving Spring Data query derivation to build time and fixing the broken finder it exposes |
| [framework/000-resilience-annotations](evals/framework/000-resilience-annotations) | [spring-framework](evals/framework) | build | medium | Framework 7's built-in retries and concurrency limit instead of a third-party retry library |
| [framework/001-api-versioning](evals/framework/001-api-versioning) | [spring-framework](evals/framework) | build | medium | Framework 7's built-in API versioning: two response shapes from one path |
| [framework/002-problem-details](evals/framework/002-problem-details) | [spring-framework](evals/framework) | build | easy | RFC 9457 problem responses instead of a default 500 |
| [framework/003-transactional-self-invocation](evals/framework/003-transactional-self-invocation) | [spring-framework](evals/framework) | fix | hard | A transaction boundary that never takes effect, diagnosed from a money-goes-missing symptom |
| [framework/004-jms-client](evals/framework/004-jms-client) | [spring-framework](evals/framework) | fix | medium | Migrating queue messaging to Framework 7's fluent client, exposing silently dropped delivery settings |
| [security/000-lockdown](evals/security/000-lockdown) | [spring-security](evals/security) | build | medium | Public reads, authenticated writes, an admin-only area, and no server-side session |
| [security/001-method-security](evals/security/001-method-security) | [spring-security](evals/security) | fix | medium | An authorization bypass through a second code path that a web-layer patch does not close |

## Agents and models

An agent config is an (agent CLI, model) pair, so comparing models within one CLI is just more files. Each config gets its own leaderboard row. The matrix ships with:

| Config | CLI | Model |
|---|---|---|
| `claude-fable-5`, `claude-opus-5`, `claude-sonnet-5`, `claude-haiku-4-5` | Claude Code | the Claude family |
| `codex-gpt-5-6-sol`, `codex-gpt-5-6-terra`, `codex-gpt-5-6-luna` | Codex | the GPT-5.6 family: Sol (flagship), Terra (balanced), Luna (cost-efficient) |
| `gemini-3-1-pro`, `gemini-3-6-flash`, `gemini-3-5-flash-lite` | Gemini CLI | the Gemini family: Pro plus the fast, cheap Flash tiers |
| `kimi-k3` | Claude Code | Kimi K3 via Moonshot's Anthropic-compatible endpoint (set `MOONSHOT_API_KEY`) |
| `grok-4-5` | Qwen Code | Grok 4.5 via xAI's OpenAI-compatible endpoint (set `XAI_API_KEY`) |

Local models via Ollama are not shipped as configs, but the [agent setup guide](docs/AGENT_SETUP.md) shows how to add them in one JSON file.

Agent selection is built into the CLI, and every selector combines with `--project`, `--difficulty`, `--eval`, `--pilot`, and `--samples`. The commands below are illustrative; paid runs also require `--allow-paid-run --max-total-cost <usd>`:

```bash
# one model family, name-prefix match (claude-*, codex-*)
./spring-evals run --family claude

# everything in agents/, one command
./spring-evals run --all-agents

# explicit picks
./spring-evals run --agent claude-fable-5,codex-gpt-5-6-sol

# cost-conscious combos
./spring-evals estimate --all-agents --project boot --samples 1
./spring-evals estimate --family codex --difficulty easy
```

Note: `--family claude` matches the agent name prefix, so `kimi-k3` (which also runs through the Claude Code CLI) stays out of Claude family comparisons.

To add a model variant, drop a JSON file in `agents/`:

```json
{
  "name": "claude-sonnet-5",
  "provider": "claude",
  "model": "claude-sonnet-5",
  "env": { "CLAUDE_CODE_OAUTH_TOKEN": "${CLAUDE_BENCH_OAUTH_TOKEN}" },
  "estCostPerAttemptUsd": 1.20
}
```

## Setup for real runs

`validate` needs nothing beyond a JDK and Docker. Scored runs drive real agent CLIs inside the benchmark image, so each agent you want on the leaderboard needs a credential and a funded account behind it; the CLIs themselves are never installed on your machine.

[Agent setup](docs/AGENT_SETUP.md) walks through every platform: signing up, getting credentials, and verifying each one. The short loop is: set up a platform, then run `./spring-evals doctor --agent <name>`. Doctor checks credentials, billing source, and endpoints as the container will see them, without sending a prompt or spending anything, and exits non-zero if any selected agent is blocked. The onboarding wizard shows the same report in the browser.

## Host context isolation

A run is only worth publishing if the agent could not see your machine's context. A global CLAUDE.md, user-installed skills, or an MCP server full of Spring docs would quietly inflate scores. On a Spring developer's machine those files are close to answer keys.

What the harness does about it:

- **Workspaces are stripped.** Candidate workspaces are created outside the repository, and agent context files (CLAUDE.md, AGENTS.md, GEMINI.md, QWEN.md, `.claude/`, `.mcp.json`, Cursor and Copilot instruction files) are removed from every fresh copy before the agent starts.
- **Every sample runs in a fresh container.** The agent CLI runs headless in a container built from one pinned image, with no host home directory, no host config files, and no host environment beyond the agent config env. Claude Code sees an empty config directory baked into the image, so host CLAUDE.md, skills, plugins, and MCP servers cannot load. Codex gets only its seeded `auth.json`. Gemini CLI and Qwen Code find no home config at all. Nothing ever runs on the host.
- **Credentials are declared, never inherited.** Interactive logins do not exist inside the container, so each config declares its credential as a `${VAR}` reference: `CLAUDE_CODE_OAUTH_TOKEN` from `claude setup-token` (subscription billing, the shipped default) or `ANTHROPIC_API_KEY` (metered API with exact per-sample costs), `GEMINI_API_KEY`, and so on. `doctor` reports the billing source from exactly what reaches the container. See [AGENT_SETUP.md](docs/AGENT_SETUP.md).
- **The judge runs in a second fresh container**, started only after the agent container is destroyed, so nothing the agent left running or planted can touch judging.
- **Every session is kept and scanned.** The CLI's own event stream is stored outside the repository, summarized into counts on the row, and scanned for references to this repository, the eval, or its hidden directories. A hit becomes a contamination flag on the row, visible on the leaderboard and in the run log.

The full policy, including residual risks like prompt-level bans, is in [Benchmark methodology](docs/METHODOLOGY.md) under Host context isolation.

## Cost warning

**Scored runs spend real money on your API accounts.** Every sample is a full agent session that reads the project, edits code, and runs Maven builds. The harness defaults to three samples per cell (pass `--samples 1` for a smoke test) and refuses paid execution unless both `--allow-paid-run` and `--max-total-cost <usd>` are present. Before each sample it reserves that agent's configured estimate and stops before the campaign cap would be exceeded. This is an estimated reservation cap, not a provider billing guarantee, so unknown-cost agents are refused and provider-side limits remain essential.

Get a projection before you spend anything; `estimate` is free and takes the same selectors as `run`:

```bash
./spring-evals estimate --all-agents --samples 1
./spring-evals estimate --all-agents --samples 3
```

For the current 16-eval catalog and the twelve shipped agents, `estimate` projects $119.52 at one sample per cell and $358.56 at the default three samples, both at API prices from the per-sample figures in `agents/*.json` (with no `spring-evals.local.json` narrowing the selection). Subscription-billed agents (Claude through a setup token, Codex through a ChatGPT sign-in) draw on plans instead, so real cash spend is usually far lower. One eval across all twelve agents is about $7.50 per sample. The full cost-control playbook, the memoization rules that decide when you pay again, the new-model-day recipe, and the open and local model story are all in the [running guide](docs/RUNNING.md).

## Dashboard

`dashboard/` is a static, Spring-branded results site. Light mode follows spring.io's look; dark mode goes full terminal. `./spring-evals report` writes `dashboard/data.json` from real results.

- `index.html`: the results view. The leaderboard with pass rate, interval, and functional rate; the all-22-suite heatmap with drill-down; run history by name with per-sample detail, contamination flags, and each run's findings summary; and spend (recorded plus an API-price estimate when cost reporting is partial). The evals list here shows only evals with results.
- `evals.html`: the full catalog. Every eval in every suite, with a blurb on what it measures and how models fail it, and empty suites linking to the proposal form.
- `models.html`: every model behind the agent configs, grouped by lab, with cost and billing notes from `dashboard/models.json`. Display metadata only; it never affects scoring.
- `onboarding.html`: the setup wizard. Served by `./spring-evals serve`, it checks the environment, saves the local agent selection, checks that credentials are set without storing them, streams a free validate, projects cost, and prints the run command. It cannot start a paid run. On static hosting it explains what each step checks and stays read-only.

Serve it locally:

```bash
./spring-evals serve            # http://localhost:4173; --port changes it
```

The server binds to loopback only, because the wizard's JSON API writes local config and launches local processes. A GitHub Actions workflow (`.github/workflows/pages.yml`) publishes `dashboard/` to GitHub Pages on every push to `main`. Pages is not enabled yet: to turn it on, open the repository's Settings, choose Pages, and set the build source to GitHub Actions.

After a run, write a findings summary to `results/runs/<run-name>.notes.md` and re-run `report`; it appears at the top of the run's log and in the dashboard's run drill-down.

## Contributing

Community benchmark ideas are very welcome. Two ways to help:

1. **Propose a benchmark.** Open a [benchmark proposal](../../issues/new?template=benchmark-proposal.yml). You describe the task and why models get it wrong. No code needed.
2. **Build an eval.** See [CONTRIBUTING.md](CONTRIBUTING.md) for the eval anatomy and authoring rules.

## Built on

Spring Evals is a plain Java harness with no framework dependencies and no agent SDK. It drives the agent CLIs headless inside Docker, judges with Maven, and keeps every measurement-critical file under a content hash. Each agent CLI is one small `AgentCli` implementation in `harness/src/main/java/dev/danvega/springevals/cli/`; adding a CLI is one class, one services line, and one pinned install line in the Dockerfile.

**[Spring Boot](https://spring.io/projects/spring-boot) and the [Spring portfolio](https://spring.io/projects)**. The subject under test. Every eval fixture is a real Spring Boot 4 project generated from [start.spring.io](https://start.spring.io), and the suite layout mirrors the portfolio's projects one to one.

**[Docker](https://www.docker.com)**. The isolation boundary and the toolchain pin. Every agent session and every judge runs in a fresh container from one image built from `harness/docker/Dockerfile` (Temurin JDK 26, Node 24, and the four CLIs at pinned versions). The image tag is derived from the Dockerfile's content, so a stale image can never serve a changed Dockerfile, and each result row records the toolchain it ran on.

**[Claude Code](https://claude.com/claude-code), [Codex](https://github.com/openai/codex), [Gemini CLI](https://github.com/google-gemini/gemini-cli), and [Qwen Code](https://github.com/QwenLM/qwen-code)**. The coding agents under test, installed in the benchmark image at pinned versions (2.1.259, 0.152.1, 0.58.0, and 0.22.3). Qwen Code also serves as the generic OpenAI-compatible driver for xAI, Ollama, and similar hosts.

Related but not a dependency: **[Agent Bench](https://github.com/markpollack/agent-bench)** by Mark Pollack (docs at [lab.pollack.ai](https://lab.pollack.ai/projects/agent-bench)) benchmarks agents on enterprise Java workflows like issue triage, PR review, and coverage improvement. Spring Evals measures a different axis: whether a model knows the framework itself. Run both if you want the full picture.

## Roadmap

- The first campaign under harness 0.6.0: the full agent matrix across all sixteen evals at three samples. It fills the empty cohort and proves the transcript capture and the CLI pins against live models.
- A hard tier. The catalog has three hard evals today; the next ones need diagnosis across several files, not API recall.
- A closed-book track through an egress allowlist on the agent container, so contamination is prevented instead of flagged.
- A controlled-model track through a Spring AI agent loop: one tool contract, token budget, and stopping rule for every model, so rows become model measurements instead of agent measurements.
- A private rotating holdout catalog, with retired tasks published for community review.
- Gradle project variants.

## License

[Apache 2.0](LICENSE)
