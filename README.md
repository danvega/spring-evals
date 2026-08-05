# Spring Evals

Evals for Spring to test how well AI models and coding agents write real Spring code.

Modern models score well on generic coding benchmarks. They do much worse on framework-specific work, especially anything released after their training data. Spring Boot 4 and Spring Framework 7 shipped breaking changes (Jackson 3, modular auto-configuration, new HTTP clients, new testing tools) that trip up even frontier models. This project measures that gap, per agent and per model, with tasks a real Spring developer would recognize.

Complementary to [Agent Bench](https://github.com/markpollack/agent-bench), which benchmarks agents on enterprise workflows. This project benchmarks framework competency: does the model actually know Spring? See [Built on](#built-on) for the projects underneath this one.

## How it works

Evals are organized into suites, one per project on [spring.io/projects](https://spring.io/projects). Every portfolio project has a suite directory under [evals/](evals), from the big ones (`framework`, `boot`, `data`, `security`, `cloud`, `ai`) to the full long tail (`batch`, `integration`, `kafka`, `amqp`, `graphql`, `grpc`, `modulith`, `session`, `authorization-server`, `hateoas`, `rest-docs`, `ldap`, `pulsar`, `shell`, `web-flow`, `web-services`). Most are empty today. Each suite README says what belongs there and links to the proposal form.

Each eval id is `<project>/<nnn>-<name>`, for example `boot/003-jackson3-migration`. The leaderboard reports an overall score plus a per-project breakdown, so you can see that a model handles Boot upgrades well but falls over on Spring AI.

Each eval is a self-contained Spring Boot project plus a task:

- `PROMPT.md` describes the task the way a developer would. Symptoms only, never the solution.
- `project/` is the workspace the agent gets. It may contain broken code (fix tasks) or a skeleton (build tasks).
- `EVAL/` holds hidden JUnit tests. The agent never sees them.
- `SOLUTION/` is a reference solution used only to validate the eval itself in CI.

The harness is a Java app in `harness/`, built on the [Spring AI Community Agent Client](https://github.com/spring-ai-community/agent-client). For each attempt it copies `project/` to a fresh temporary workspace outside the repository and runs the configured coding agent. After the agent exits, the harness removes candidate tests, restores the trusted Maven launcher, injects hidden tests, applies deterministic mechanism checks, and verifies that every hidden test actually executed. The default is one attempt; retries are explicit.

Metrics:

- **Pass@1**: passed on attempt 1; the primary capability metric
- **Pass@k**: passed within the explicitly authorized attempt budget
- **95% Wilson interval**: makes uncertainty from a small binary task set visible
- **Coverage**: attempted evals versus the full catalog; partial rows are not leaderboard-eligible
- **Avg task duration**, **avg task cost**, and **cost per pass**, aggregating every attempt spent on a task

Rows measure an **agent configuration**: model plus coding-agent CLI, tool policy, and runtime. They are not pure model measurements. See [Benchmark methodology](docs/METHODOLOGY.md) for the controlled-model track design.

## Quick start

Requirements: JDK 25+ and network access for Maven. Scored runs also need agent CLIs set up; see [Setup for real runs](#setup-for-real-runs) and read the [cost warning](#cost-warning) first.

```bash
# see available evals
./spring-evals list

# verify agent CLIs, credential sources, env references, and local endpoints
# no prompt is sent and no generation request is made
./spring-evals doctor
./spring-evals doctor --family codex
./spring-evals doctor --agent claude-sonnet-5,gemini-2-5-pro

# check every eval is well-formed (broken fails, solution passes)
./spring-evals validate

# inspect the three-task pilot and its maximum cost (no model calls)
./spring-evals list
./spring-evals estimate --agent claude-sonnet-5 --pilot --attempts 1

# paid execution is locked until both intent and a campaign cap are explicit
./spring-evals run --agent claude-sonnet-5 --pilot --attempts 1 \
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
| [security/000-lockdown](evals/security/000-lockdown) | spring-security | build | medium | A stateless SecurityFilterChain with public reads, authenticated writes, and an ADMIN area |
| [framework/000-resilience-annotations](evals/framework/000-resilience-annotations) | spring-framework | build | medium | Core @Retryable and @ConcurrencyLimit instead of adding Spring Retry or Resilience4j |
| [framework/001-api-versioning](evals/framework/001-api-versioning) | spring-framework | build | medium | Framework 7 native API versioning: two shapes, one path, header-selected |
| [framework/002-problem-details](evals/framework/002-problem-details) | spring-framework | build | easy | RFC 9457 problem details with ProblemDetail and a controller advice |
| [framework/003-transactional-self-invocation](evals/framework/003-transactional-self-invocation) | spring-framework | fix | hard | The @Transactional self-invocation proxy trap, diagnosed from a money-goes-missing symptom |
| [data/000-n-plus-one](evals/data/000-n-plus-one) | spring-data | fix | hard | Recognizing and fixing an N+1 query pattern from a symptom description, verified by statement counts |

## Agents and models

An agent config is an (agent CLI, model) pair, so comparing models within one CLI is just more files. Each config gets its own leaderboard row. The matrix ships with:

| Config | CLI | Model |
|---|---|---|
| `claude-fable-5`, `claude-opus-5`, `claude-sonnet-5`, `claude-haiku-4-5` | Claude Code | the Claude family |
| `codex-gpt-5-6-sol`, `codex-gpt-5-6-terra`, `codex-gpt-5-6-luna` | Codex | the GPT-5.6 family: Sol (flagship), Terra (balanced), Luna (cost-efficient) |
| `gemini-2-5-pro` | Gemini CLI | gemini-2.5-pro |
| `kimi-k3` | Claude Code | Kimi K3 via Moonshot's Anthropic-compatible endpoint |
| `qwen3-coder-ollama`, `gpt-oss-ollama` | Qwen Code | local models via Ollama |

Agent selection is built into the CLI, and every selector combines with `--project`, `--difficulty`, `--eval`, `--pilot`, and `--attempts`. The commands below are illustrative; paid runs also require `--allow-paid-run --max-total-cost <usd>`:

```bash
# one model family, name-prefix match (claude-*, codex-*)
./spring-evals run --family claude

# everything in agents/, one command
./spring-evals run --all-agents

# explicit picks
./spring-evals run --agent claude-fable-5,codex-gpt-5-6-sol

# cost-conscious combos
./spring-evals estimate --all-agents --project boot --attempts 1
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

`validate` needs nothing beyond a JDK. Scored runs drive real agent CLIs, so each agent you want on the leaderboard needs its CLI installed and authenticated:

Run `./spring-evals doctor` first. It exits non-zero if any selected agent is blocked. It checks configuration syntax, CLI presence and version, `${ENV_VAR}` references, known local OAuth/auth files, Claude's non-generative auth status, cost estimates, and localhost model endpoints. It never prints credential values, sends a prompt, or tests a remote API key. Remote credential validity must still be confirmed in the provider console.

- **Claude models**: [Claude Code](https://claude.com/claude-code) installed and logged in (`claude` on your PATH). Runs use `--dangerously-skip-permissions` equivalent autonomy, so use an account you are comfortable spending from.
- **Codex**: the `codex` CLI installed and authenticated with your OpenAI account.
- **Gemini**: the `gemini` CLI installed, with `GEMINI_API_KEY` set or OAuth login done.
- **Kimi K3**: no extra CLI. It runs through Claude Code against Moonshot's endpoint; set `MOONSHOT_API_KEY` in your shell.
- **Local models**: [Ollama](https://ollama.com) running, models pulled first (`ollama pull qwen3-coder:30b`, about 20GB on disk), and the `qwen` CLI installed. Free to run, but slow on laptop hardware.

## Host context isolation

A run is only worth publishing if the agent could not see your machine's context. A global CLAUDE.md, user-installed skills, or an MCP server full of Spring docs would quietly inflate scores. On a Spring developer's machine those files are close to answer keys.

What the harness does about it:

- **Workspaces are stripped.** Candidate workspaces are created outside the repository, and agent context files (CLAUDE.md, AGENTS.md, GEMINI.md, QWEN.md, `.claude/`, `.mcp.json`, Cursor and Copilot instruction files) are removed from every fresh copy before the agent starts.
- **Claude Code runs without filesystem settings.** In SDK mode the Claude CLI defaults to loading no host CLAUDE.md, no skills, and no MCP servers. Known limit: the current Agent Client adapter cannot pass the flag explicitly, so this rests on the CLI default. Verify it once per CLI version before publishing results; `doctor` surfaces this assumption as a warning so it is never silently trusted.
- **Other CLIs are checked, not controlled.** Codex, Gemini CLI, and Qwen Code read global context files and MCP settings the harness cannot disable per run. `doctor` warns when they exist (`~/.codex/AGENTS.md`, `~/.gemini/GEMINI.md`, `settings.json` MCP config, and so on). For published campaigns, run these CLIs in a container or a clean account until the warning list is empty.

The full policy, including residual risks like prompt-level bans, is in [Benchmark methodology](docs/METHODOLOGY.md) under Host context isolation.

## Cost warning

**Scored runs spend real money on your API accounts.** Every attempt is a full agent session that reads the project, edits code, and runs Maven builds. The harness defaults to one attempt and refuses paid execution unless both `--allow-paid-run` and `--max-total-cost <usd>` are present. Before each attempt it reserves that agent's configured estimate and stops before the campaign cap would be exceeded. This is an estimated reservation cap—not a provider billing guarantee—so unknown-cost agents are refused and provider-side limits remain essential.

Get a projection before you spend anything:

```bash
./spring-evals estimate --attempts 1    # every agent, full first-try suite
./spring-evals estimate --agent claude-sonnet-5 --attempts 2
./spring-evals estimate --all-agents --pilot --attempts 1
```

Estimates come from `estCostPerAttemptUsd` in each agent config; tune them as your real numbers come in. For the current 9-eval suite, the full 11-agent matrix projects to roughly $90 expected and $220 absolute worst case, with the three frontier models accounting for about two thirds of it.

Ways to control spend:

- Start with the three-eval `--pilot --attempts 1` suite, then expand only promising rows.
- Keep `--max-total-cost` below the provider-side budget. The harness cap is a reservation guard, not a replacement for provider billing limits.
- A later run with more attempts only pays for version-matched evals that have not already passed.
- Smoke-test a new agent with `--difficulty easy` (2 evals) before committing to a full run.
- Scope runs with `--eval` or `--project` while iterating.
- Claude-family agents carry a `budgetUsd` per-attempt hard cap in their config, enforced by the CLI itself. Set per-model spend limits in other providers' dashboards.
- Every eval project builds with `-ntp`, so agents do not burn tokens reading Maven download logs.
- Actual recorded spend shows up in the report output, `results/leaderboard.md`, and the dashboard's Benchmark spend tile. Cost is recorded automatically for Claude Code runs; for other CLIs, check the provider dashboard.

## New model day

A new model dropped and claims the crown. To get a comparable row on your leaderboard:

```bash
# 1. describe it (one JSON file)
cat > agents/new-hotness.json <<'JSON'
{
  "name": "new-hotness",
  "provider": "claude",
  "model": "the-new-model-id",
  "budgetUsd": 3.00,
  "estCostPerAttemptUsd": 1.00
}
JSON

# 2. see what it will cost
./spring-evals estimate --agent new-hotness

# 3. cheap first pass, then the full treatment if it earns it
./spring-evals run --agent new-hotness --pilot --attempts 1 --allow-paid-run --max-total-cost 5
./spring-evals run --agent new-hotness --attempts 1 --allow-paid-run --max-total-cost 15

# 4. refresh the leaderboard and dashboard
./spring-evals report
```

Every existing row stays comparable because the evals did not change under it.

Supported providers today: `claude`, `codex`, `gemini`, and `qwen-code` (the Agent Client adapters). A new model on an existing provider is just another JSON file. A new provider is one more case in `harness/.../Agents.java`, or a custom `AgentModel` for CLIs Agent Client does not cover yet.

The optional `env` map is passed to the agent CLI process, and values can reference host environment variables with `${VAR}`.

## Open and local models

Frontier models are only half the story. The same harness runs open-weight models by pointing an agent CLI at a compatible endpoint:

**Local via Ollama.** The Qwen Code CLI speaks to any OpenAI-compatible endpoint, so any model Ollama serves works:

```bash
ollama pull qwen3-coder:30b
./spring-evals run --agent qwen3-coder-ollama
```

See [agents/qwen3-coder-ollama.json](agents/qwen3-coder-ollama.json) and [agents/gpt-oss-ollama.json](agents/gpt-oss-ollama.json). Swap the model name to eval anything in the Ollama library.

**Local via Docker Model Runner.** Same pattern, different server. Point `OPENAI_BASE_URL` at `http://localhost:12434/engines/v1` and use `docker model pull` instead of Ollama.

**Hosted open-weight models.** Providers with Anthropic-compatible endpoints run through the Claude Code adapter unchanged. [agents/kimi-k3.json](agents/kimi-k3.json) runs Kimi K3 through Moonshot's endpoint (set `MOONSHOT_API_KEY`). OpenAI-compatible hosts (Together, Fireworks, DeepSeek, and the rest) follow the qwen-code pattern with a different base URL and API key.

Cost for local models reports as n/a. Duration still comes through, which is half the story for local models anyway.

## Dashboard

`dashboard/` is a static, Spring-branded results page: overall leaderboard, per-project matrix, and the eval list. Light mode follows spring.io's look; dark mode goes full terminal. `./spring-evals report` writes `dashboard/data.json` from real results (until then the page shows clearly labeled sample data). Serve it anywhere static, including GitHub Pages:

```bash
python3 -m http.server 4173 --directory dashboard
```

## Contributing

Community benchmark ideas are very welcome. Two ways to help:

1. **Propose a benchmark.** Open a [benchmark proposal](../../issues/new?template=benchmark-proposal.yml). You describe the task and why models get it wrong. No code needed.
2. **Build an eval.** See [CONTRIBUTING.md](CONTRIBUTING.md) for the eval anatomy and authoring rules.

## Built on

Spring Evals is a thin layer over open source projects that do the heavy lifting. If you want to understand or extend the harness, these are the projects to learn:

**[Agent Client](https://github.com/spring-ai-community/agent-client)** (Spring AI Community, `org.springaicommunity.agents`, v0.16.0). A portable Java API for driving autonomous CLI coding agents: Claude Code, Codex, Gemini CLI, Qwen Code, Amazon Q, and Amp behind one `AgentClient` interface, modeled after Spring AI's `ChatClient`. It is why this harness can add a new agent with a JSON file instead of parsing another CLI's output format, and why duration and cost come back uniformly across providers. Docs: [Spring AI Community docs](https://springaicommunity.mintlify.app/projects/incubating/agent-client), intro post: [Introducing Agent Client and Agent Bench](https://spring.io/blog/2025/10/28/agents-and-benchmarks/).

**[Agent Judge](https://github.com/spring-ai-community/agent-judge)** (Spring AI Community, `org.springaicommunity:agent-judge-*`, v0.9.1). A verdict framework for agent output: a `Judge` interface, deterministic judges like `CommandJudge` and `BuildSuccessJudge`, LLM judges, and jury patterns for combining them. Our pass/fail verdict is its `CommandJudge` running `./mvnw clean test` against the hidden tests. Its jury support is the path to a future idiom-scoring tier.

**[Agent Sandbox](https://github.com/spring-ai-community/agent-sandbox)** (Spring AI Community, `org.springaicommunity:agent-sandbox-*`, v0.9.1). An execution abstraction with local and Docker implementations. Judge commands run through it today via `LocalSandbox`; `DockerSandbox` is the drop-in upgrade for container isolation on the roadmap.

**[Spring Boot](https://spring.io/projects/spring-boot) and the [Spring portfolio](https://spring.io/projects)**. The subject under test. Every eval fixture is a real Spring Boot 4 project generated from [start.spring.io](https://start.spring.io), and the suite layout mirrors the portfolio's projects one to one.

Related but not a dependency: **[Agent Bench](https://github.com/markpollack/agent-bench)** by Mark Pollack (docs at [lab.pollack.ai](https://lab.pollack.ai/projects/agent-bench)) benchmarks agents on enterprise Java workflows like issue triage, PR review, and coverage improvement, using the same Agent Client and judge foundations. Spring Evals measures a different axis: whether a model knows the framework itself. Run both if you want the full picture.

## Roadmap

- More evals: modular auto-config, HTTP interface clients, API versioning, resilience, RestTestClient, null safety, Spring Data AOT, security
- OS/container isolation in addition to the current out-of-repository workspace separation
- A controlled-model track with one agent loop, tool contract, token budget, and network policy
- A private rotating holdout catalog, with retired tasks published for community review
- More deterministic architecture checks, with an LLM judge used only as supplemental qualitative evidence
- Published leaderboard page with regularly refreshed results
- Gradle project variants

## License

[Apache 2.0](LICENSE)
