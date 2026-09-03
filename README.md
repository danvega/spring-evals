# Spring Evals

Evals for Spring to test how well AI models and coding agents write real Spring code.

**New here?** The [getting started guide](docs/GETTING_STARTED.md) takes you from clone to your first scored run in about ten minutes, one agent, a dollar or two. The rest of this README is the full reference.

Modern models score well on generic coding benchmarks. They do much worse on framework-specific work, especially anything released after their training data. Spring Boot 4 and Spring Framework 7 shipped breaking changes (Jackson 3, modular auto-configuration, new HTTP clients, new testing tools) that trip up even frontier models. This project measures that gap, per agent and per model, with tasks a real Spring developer would recognize.

Complementary to [Agent Bench](https://github.com/markpollack/agent-bench), which benchmarks agents on enterprise workflows. This project benchmarks framework competency: does the model actually know Spring? See [Built on](#built-on) for the projects underneath this one.

## What it produces

Real results from a real run (12 agents, one build eval, one attempt each). The task: write a new Spring Boot 4 project from an empty repository to the standard start.spring.io would produce today. Seven agents passed and three failed on the merits, each making the same mistake: pre-Boot-4 conventions, caught by hidden mechanism checks. Two agents hit infrastructure errors, so they are excluded from scoring instead of being counted as model failures; that is why the dashboard header counts 10 agents with verdicts. The two "at API prices" figures differ by definition: the header estimates actual samples, the run log projects the configured per-sample estimates.

![Leaderboard with per-agent Pass@1, confidence intervals, tokens, and cost](docs/images/dashboard-leaderboard.png)

Every run is recorded by name with its own scoreboard and a findings write-up, so infrastructure failures are never mistaken for model failures:

![Run detail with findings narrative and per-agent scoreboard](docs/images/dashboard-run-detail.png)

Coverage across the Spring portfolio, with room to grow into all 22 project suites:

![Per-project heatmap](docs/images/dashboard-projects.png)

There is also a terminal-style dark mode:

![Dark mode dashboard](docs/images/dashboard-dark.png)

## How it works

Evals are organized into suites, one per project on [spring.io/projects](https://spring.io/projects). Every portfolio project has a suite directory under [evals/](evals), from the big ones (`framework`, `boot`, `data`, `security`, `cloud`, `ai`) to the full long tail (`batch`, `integration`, `kafka`, `amqp`, `graphql`, `grpc`, `modulith`, `session`, `authorization-server`, `hateoas`, `rest-docs`, `ldap`, `pulsar`, `shell`, `web-flow`, `web-services`). Most are empty today. Each suite README says what belongs there and links to the proposal form.

Each eval id is `<project>/<nnn>-<name>`, for example `boot/003-jackson3-migration`. The leaderboard reports an overall score plus a per-project breakdown, so you can see that a model handles Boot upgrades well but falls over on Spring AI.

Each eval is a self-contained Spring Boot project plus a task:

- `PROMPT.md` describes the task the way a developer would. Symptoms only, never the solution.
- `project/` is the workspace the agent gets. It may contain broken code (fix tasks) or a skeleton (build tasks).
- `EVAL/` holds hidden JUnit tests. The agent never sees them.
- `SOLUTION/` is a reference solution used only to validate the eval itself in CI.

The harness is a plain Java app in `harness/`. For each sample it copies `project/` to a fresh workspace outside the repository and runs the configured coding-agent CLI headless in a fresh container from one pinned image. After the agent container is destroyed, the harness removes candidate tests, restores the trusted Maven launcher, injects hidden tests, applies deterministic mechanism checks, and runs the judge in a second fresh container, verifying that every hidden test actually executed. Every (agent, eval) cell runs three independent samples by default; `--samples` changes that.

Metrics:

- **Pass rate**: samples whose hidden tests passed and whose idiom checks held, over all verdict samples; the primary capability metric
- **Functional rate**: samples whose hidden tests passed with or without the idiom; the gap to the pass rate is the share of working but last-generation solutions
- **95% Wilson interval**: makes uncertainty from a small sample count visible
- **Coverage**: evals with at least one verdict sample versus the full catalog; partial rows are not leaderboard-eligible
- **Avg duration**, **avg cost**, and **cost per pass**, per sample

Rows measure an **agent configuration**: model plus coding-agent CLI, tool policy, and runtime. They are not pure model measurements. See [Benchmark methodology](docs/METHODOLOGY.md) for the controlled-model track design.

## Quick start

Requirements: JDK 26+, Docker, and network access for Maven (SDKMAN users: `sdk env` activates the pinned JDK from `.sdkmanrc`). Every agent and every judge runs in a container, so Docker must be running even for `validate`. Scored runs also need credentials; see [Setup for real runs](#setup-for-real-runs) and read the [cost warning](#cost-warning) first.

```bash
# see available evals
./spring-evals list

# verify credentials, billing sources, env references, and endpoints as the container sees them
# no prompt is sent and no generation request is made
./spring-evals doctor
./spring-evals doctor --family codex
./spring-evals doctor --agent claude-sonnet-5,gemini-3-1-pro
./spring-evals doctor --docker      # also build the image and probe every CLI inside it

# check every eval is well-formed (broken fails; solution and alternatives pass; workarounds are functional only)
./spring-evals validate

# inspect the three-task pilot and its maximum cost (no model calls)
./spring-evals list
./spring-evals estimate --agent claude-sonnet-5 --pilot --samples 1

# paid execution is locked until both intent and a campaign cap are explicit
./spring-evals run --agent claude-sonnet-5 --pilot --samples 1 \
  --allow-paid-run --max-total-cost 2.00

# run just one project's suite, or one eval
./spring-evals estimate --agent claude-sonnet-5 --project boot
./spring-evals estimate --agent claude-sonnet-5 --eval boot/003-jackson3-migration

# print the leaderboard from accumulated results
./spring-evals report
```

Results accumulate in `results/results.json`. Cache identity includes the eval content, agent configuration, and harness version, so edited prompts, tests, configs, or judging code cannot silently reuse stale results. Pass `--force` to rerun the current identity.

## Current evals

| Eval | Project | Type | Difficulty | What it tests |
|---|---|---|---|---|
| [boot/000-initializr-parity](evals/boot/000-initializr-parity) | spring-boot | build | medium | Generating a new Boot 4 project from scratch, judged against the Spring Initializr bar |
| [boot/001-modular-autoconfig](evals/boot/001-modular-autoconfig) | spring-boot | fix | medium | Diagnosing features that silently vanished under Boot 4 modular auto-configuration (Flyway, H2 console) |
| [boot/002-restclient-migration](evals/boot/002-restclient-migration) | spring-boot | fix | easy | Migrating RestTemplate code to the auto-configured RestClient with the right starter |
| [boot/003-jackson3-migration](evals/boot/003-jackson3-migration) | spring-boot | fix | medium | Migrating Jackson 2 code to the Jackson 3 / `tools.jackson` stack while preserving the API's JSON contract |
| [boot/004-flyway-module](evals/boot/004-flyway-module) | spring-boot | fix | medium | Diagnosing Flyway migrations that silently stopped running on Boot 4, masked by Hibernate recreating empty tables |
| [boot/005-h2-console](evals/boot/005-h2-console) | spring-boot | fix | easy | Restoring the H2 console after Boot 4 moved it into a dedicated auto-configuration module |

## Agents and models

An agent config is an (agent CLI, model) pair, so comparing models within one CLI is just more files. Each config gets its own leaderboard row. The matrix ships with:

| Config | CLI | Model |
|---|---|---|
| `claude-fable-5`, `claude-opus-5`, `claude-sonnet-5`, `claude-haiku-4-5` | Claude Code | the Claude family |
| `codex-gpt-5-6-sol`, `codex-gpt-5-6-terra`, `codex-gpt-5-6-luna` | Codex | the GPT-5.6 family: Sol (flagship), Terra (balanced), Luna (cost-efficient) |
| `gemini-3-1-pro`, `gemini-3-6-flash`, `gemini-3-5-flash-lite` | Gemini CLI | the Gemini family: Pro plus the fast, cheap Flash tiers |
| `kimi-k3` | Claude Code | Kimi K3 via Moonshot's Anthropic-compatible endpoint |
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
  "model": "claude-sonnet-5"
}
```

## Setup for real runs

`validate` needs nothing beyond a JDK and Docker. Scored runs drive real agent CLIs inside the benchmark image, so each agent you want on the leaderboard needs a credential and a funded account behind it; the CLIs themselves are never installed on your machine.

[Agent setup](docs/AGENT_SETUP.md) walks through every platform: signing up, getting credentials, and verifying each one. The short loop is: set up a platform, then run `./spring-evals doctor --agent <name>`. Doctor checks credentials, billing source, and endpoints as the container will see them, without sending a prompt or spending anything, and exits non-zero if any selected agent is blocked.

## Host context isolation

A run is only worth publishing if the agent could not see your machine's context. A global CLAUDE.md, user-installed skills, or an MCP server full of Spring docs would quietly inflate scores. On a Spring developer's machine those files are close to answer keys.

What the harness does about it:

- **Workspaces are stripped.** Candidate workspaces are created outside the repository, and agent context files (CLAUDE.md, AGENTS.md, GEMINI.md, QWEN.md, `.claude/`, `.mcp.json`, Cursor and Copilot instruction files) are removed from every fresh copy before the agent starts.
- **Every sample runs in a fresh container.** The agent CLI runs headless in a container built from one pinned image, with no host home directory, no host config files, and no host environment beyond the agent config env. Claude Code sees an empty config directory baked into the image, so host CLAUDE.md, skills, plugins, and MCP servers cannot load. Codex gets only its seeded `auth.json`. Gemini CLI and Qwen Code find no home config at all. There is no host execution mode.
- **Credentials are declared, never inherited.** Interactive logins do not exist inside the container, so each config declares its credential as a `${VAR}` reference: `CLAUDE_CODE_OAUTH_TOKEN` from `claude setup-token` (subscription billing, the shipped default) or `ANTHROPIC_API_KEY` (metered API with exact per-attempt costs), `GEMINI_API_KEY`, and so on. `doctor` reports the billing source from exactly what reaches the container. See [AGENT_SETUP.md](docs/AGENT_SETUP.md).
- **The judge runs in a second fresh container**, started only after the agent container is destroyed, so nothing the agent left running or planted can touch judging.

The full policy, including residual risks like prompt-level bans, is in [Benchmark methodology](docs/METHODOLOGY.md) under Host context isolation.

## Cost warning

**Scored runs spend real money on your API accounts.** Every sample is a full agent session that reads the project, edits code, and runs Maven builds. The harness defaults to three samples per cell (pass `--samples 1` for a smoke test) and refuses paid execution unless both `--allow-paid-run` and `--max-total-cost <usd>` are present. Before each sample it reserves that agent's configured estimate and stops before the campaign cap would be exceeded. This is an estimated reservation cap, not a provider billing guarantee, so unknown-cost agents are refused and provider-side limits remain essential.

Get a projection before you spend anything; `estimate` is free and takes the same selectors as `run`:

```bash
./spring-evals estimate --all-agents --pilot --samples 1
```

For the current six-eval suite, the full 12-agent matrix at one sample per cell projects to roughly $45 at API prices, and the default three samples to roughly $135. Subscription-billed agents (Claude, ChatGPT, Google sign-ins) draw on plans instead, so real cash spend is usually far lower. A single eval across the paid agents is about $6. The full cost-control playbook, the memoization rules that decide when you pay again, the new-model-day recipe, and the open and local model story are all in the [running guide](docs/RUNNING.md).

## Dashboard

`dashboard/` is a static, Spring-branded results site. Light mode follows spring.io's look; dark mode goes full terminal. `./spring-evals report` writes `dashboard/data.json` from real results.

- `index.html`: the results view: leaderboard, all-22-suite heatmap with drill-down, run history by name with per-sample detail and each run's findings summary, and spend (recorded plus an API-price estimate when cost reporting is partial). The evals list here shows only evals with results.
- `evals.html`: the full catalog: every eval in every suite, with empty suites linking to the proposal form.
- `onboarding.html`: the setup wizard. Served by `./spring-evals serve`, it checks the environment, saves the local agent selection, checks that credentials are set without storing them, streams a free validate, projects cost, and prints the run command. On static hosting it explains what each step checks and stays read-only.

Serve it with the built-in JDK file server (or any static host, including GitHub Pages):

```bash
./spring-evals serve
```

After a run, write a findings summary to `results/runs/<run-name>.notes.md` and re-run `report`; it appears at the top of the run's log and in the dashboard's run drill-down.

## Contributing

Community benchmark ideas are very welcome. Two ways to help:

1. **Propose a benchmark.** Open a [benchmark proposal](../../issues/new?template=benchmark-proposal.yml). You describe the task and why models get it wrong. No code needed.
2. **Build an eval.** See [CONTRIBUTING.md](CONTRIBUTING.md) for the eval anatomy and authoring rules.

## Built on

Spring Evals is a plain Java harness with no framework dependencies. It drives the agent CLIs headless inside Docker, judges with Maven, and keeps every measurement-critical file under a content hash. Each agent CLI is one small `AgentCli` implementation in `harness/src/main/java/dev/danvega/springevals/cli/`; adding a CLI is one class, one services line, and one pinned install line in the Dockerfile.

**[Spring Boot](https://spring.io/projects/spring-boot) and the [Spring portfolio](https://spring.io/projects)**. The subject under test. Every eval fixture is a real Spring Boot 4 project generated from [start.spring.io](https://start.spring.io), and the suite layout mirrors the portfolio's projects one to one.

**[Claude Code](https://claude.com/claude-code), [Codex](https://github.com/openai/codex), [Gemini CLI](https://github.com/google-gemini/gemini-cli), and [Qwen Code](https://github.com/QwenLM/qwen-code)**. The coding agents under test, installed at pinned versions in the benchmark image.

Related but not a dependency: **[Agent Bench](https://github.com/markpollack/agent-bench)** by Mark Pollack (docs at [lab.pollack.ai](https://lab.pollack.ai/projects/agent-bench)) benchmarks agents on enterprise Java workflows like issue triage, PR review, and coverage improvement, built on the Spring AI Community agent projects. Spring Evals measures a different axis: whether a model knows the framework itself. Run both if you want the full picture.

## Roadmap

- More evals: modular auto-config, HTTP interface clients, API versioning, resilience, RestTestClient, null safety, Spring Data AOT, security
- A controlled-model track with one agent loop, tool contract, token budget, and network policy
- A private rotating holdout catalog, with retired tasks published for community review
- More deterministic architecture checks, with an LLM judge used only as supplemental qualitative evidence
- Published leaderboard page with regularly refreshed results
- Gradle project variants

## License

[Apache 2.0](LICENSE)
