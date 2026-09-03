# Spring Evals

A benchmark that measures how well AI models and coding agents write real Spring code.

Modern models score well on generic coding benchmarks and much worse on framework-specific work, especially anything released after their training data. Spring Boot 4 and Spring Framework 7 shipped breaking changes that trip up even frontier models. This project measures that gap with tasks a real Spring developer would recognize.

Each eval is a broken or empty Spring Boot project plus a task written the way a colleague would describe the symptom. The agent works in a fresh container. Hidden tests it never sees decide whether the code works, and separate checks decide whether it reached for the modern Spring mechanism or an older workaround.

## Try it, free

You need JDK 26 and Docker running. No API keys, no spending.

```bash
git clone https://github.com/danvega/spring-evals.git
cd spring-evals
./spring-evals serve
```

Open <http://localhost:4173/onboarding.html>. The wizard checks your setup, then streams a real eval through the judge so you watch the broken project fail and the reference solution pass. Give it a few minutes the first time, because it builds the benchmark image.

Prefer the terminal? `./spring-evals validate boot/002-restclient-migration` does the same thing.

Scored runs need agent credentials and spend real money. The wizard ends by printing that command; it cannot start one itself. The [getting started guide](docs/GETTING_STARTED.md) walks the whole path.

## What it measures

Sixteen evals across five Spring projects. The suite READMEs say what each one measures and how models fail it.

| Eval | Project | Type | Difficulty | What it tests |
|---|---|---|---|---|
| [ai/000-chatclient-basics](evals/ai/000-chatclient-basics) | [spring-ai](evals/ai) | fix | medium | Structured output through the framework against a stub model that answers in prose unless asked for a shape |
| [boot/000-initializr-parity](evals/boot/000-initializr-parity) | [spring-boot](evals/boot) | build | medium | Generating a new Boot 4 project from scratch, judged against the Spring Initializr bar |
| [boot/001-modular-autoconfig](evals/boot/001-modular-autoconfig) | [spring-boot](evals/boot) | fix | medium | Diagnosing two features that silently vanished when Boot 4 split its auto-configuration into modules |
| [boot/002-restclient-migration](evals/boot/002-restclient-migration) | [spring-boot](evals/boot) | fix | easy | Migrating HTTP client code that no longer compiles to Boot 4's current client stack |
| [boot/003-jackson3-migration](evals/boot/003-jackson3-migration) | [spring-boot](evals/boot) | fix | medium | Migrating Jackson 2 code to Jackson 3 while preserving the API's JSON contract |
| [boot/004-flyway-module](evals/boot/004-flyway-module) | [spring-boot](evals/boot) | fix | medium | Diagnosing Flyway migrations that silently stopped running on Boot 4, masked by the ORM recreating empty tables |
| [boot/005-h2-console](evals/boot/005-h2-console) | [spring-boot](evals/boot) | fix | easy | Restoring the H2 console after Boot 4 moved it out of the monolithic auto-configuration |
| [data/000-n-plus-one](evals/data/000-n-plus-one) | [spring-data](evals/data) | fix | hard | Recognizing and fixing an N+1 query pattern from a symptom description, verified by query volume |
| [data/001-repository-aot](evals/data/001-repository-aot) | [spring-data](evals/data) | fix | hard | Moving Spring Data query derivation to build time and fixing the broken finder it exposes |
| [framework/000-resilience-annotations](evals/framework/000-resilience-annotations) | [spring-framework](evals/framework) | build | medium | Framework 7's built-in retries and concurrency limit instead of a third-party retry library |
| [framework/001-api-versioning](evals/framework/001-api-versioning) | [spring-framework](evals/framework) | build | medium | Framework 7's built-in API versioning, selected by request header with a default version |
| [framework/002-problem-details](evals/framework/002-problem-details) | [spring-framework](evals/framework) | build | easy | RFC 9457 problem responses instead of a default 500 |
| [framework/003-transactional-self-invocation](evals/framework/003-transactional-self-invocation) | [spring-framework](evals/framework) | fix | hard | A transaction boundary that never takes effect, diagnosed from a money-goes-missing symptom |
| [framework/004-jms-client](evals/framework/004-jms-client) | [spring-framework](evals/framework) | fix | medium | Migrating queue messaging to Framework 7's fluent client, exposing silently dropped delivery settings |
| [security/000-lockdown](evals/security/000-lockdown) | [spring-security](evals/security) | build | medium | Public reads, authenticated writes, an admin-only area, and no server-side session |
| [security/001-method-security](evals/security/001-method-security) | [spring-security](evals/security) | fix | medium | An authorization bypass through a second code path that a web-layer patch does not close |

Seventeen more suites exist and are empty, one per remaining project on [spring.io/projects](https://spring.io/projects). Each links to the proposal form.

## Results

`./spring-evals report` builds a leaderboard, a per-project heatmap, and a log for every run.

![The dashboard leaderboard, showing per-agent pass columns with confidence intervals, tokens, and cost](docs/images/dashboard-leaderboard.png)

There are no results yet. Harness 0.6.0 reworked how a verdict is reached, so every earlier run is archived in [results/archive](results/archive) rather than shown: the old judge checked idiom before running the tests, and at least one check was later proved wrong. Those runs measured a different thing, and the whole eval catalog has changed under them since. The board fills when the first campaign runs under this version. The screenshot above is from an earlier run and shows the older columns.

Two numbers per row, not one. **Pass rate** counts samples where the hidden tests passed and the modern mechanism was used. **Functional rate** counts samples where the tests passed at all. The gap between them is the share of solutions that work but are written in last year's Spring, which is the thing this benchmark exists to see. Every cell runs three samples by default, and a row needs a verdict on every eval to be leaderboard-eligible. [Methodology](docs/METHODOLOGY.md) defines all of it.

## How it stays honest

A benchmark is worth publishing only if the agent could not see the answer. On a Spring developer's machine, a global CLAUDE.md or an installed docs server is close to an answer key.

- Every sample runs in a fresh container from one pinned image. No host home, no host config, and no host environment beyond the credential the agent config declares.
- The judge runs in a second fresh container, started after the agent's is destroyed, so nothing the agent left behind can reach it.
- Hidden tests, reference solutions, and checks live outside the workspace the agent gets.
- Each eval ships alternative solutions that must pass and workarounds that must be caught, so a check cannot quietly reject a legitimate answer.
- Every agent session is kept and scanned for references to this repository or the eval. A hit is flagged on the row, and excluding it is a human decision.

Details, including the residual risks, are in [Methodology](docs/METHODOLOGY.md).

## Agents and models

An agent config is one (CLI, model) pair in a JSON file, so comparing models within one CLI is just more files. Each config gets its own leaderboard row. Rows measure an agent configuration, not a bare model.

Several models are kept alongside the generation before them, so the leaderboard answers whether a new release actually gains ground on Spring work rather than only what the newest one scores.

| CLI | Models |
|---|---|
| Claude Code | Claude Fable 5.1 and 5, Opus 5 and 4.8, Sonnet 5, Haiku 4.5, and Kimi K3 through Moonshot |
| Codex | GPT-5.6 Sol, Terra, and Luna |
| Gemini CLI | Gemini 3.1 Pro, 3.8 and 3.6 Flash, and 3.5 Flash Lite |
| Qwen Code | Grok 4.6 and 4.5 through xAI, and anything else OpenAI-compatible including Ollama |

The CLIs live in the benchmark image, so nothing is installed on your machine. [Agent setup](docs/AGENT_SETUP.md) covers credentials per platform, and `./spring-evals doctor` verifies them without sending a prompt.

## Cost

Most of what this project does is free, and a full scored run costs less than a coffee. Only a cross-model campaign is expensive.

| What you run | Cost | What you get |
|---|---|---|
| `./spring-evals validate` | free | All 16 evals judged end to end, proving the benchmark is honest |
| Pilot, three evals, fast model | about $0.15 | The whole paid pipeline, start to finish |
| **Full catalog, one fast model** | **about $0.80** | **A real leaderboard row across every eval** |
| Full catalog, three samples | about $2.40 | That row with a meaningful confidence interval |
| All 16 agents, three samples | about $524 | Cross-model numbers worth publishing |

The cheap rows are not a reduced mode. They run the same evals through the same judge. The only difference is which model does the work: Gemini 3.5 Flash Lite costs $0.05 a sample where Opus 5 costs $1.45. Start there, and spend on a frontier model once you want its number specifically.

Local models through Ollama cost nothing at all. The [agent setup guide](docs/AGENT_SETUP.md) has the one JSON file it takes.

Two guards, and one thing they do not cover. Paid execution refuses to start without `--allow-paid-run` and `--max-total-cost`, and `estimate` takes the same selectors as `run` so you always see the projection first:

```bash
./spring-evals estimate --agent gemini-3-5-flash-lite --samples 1
```

What the campaign cap cannot do is stop a single runaway sample. It reserves each sample's estimate beforehand and halts before the cap is breached, but a sample that costs far more than its estimate is already paid for by the time the harness sees the number. The estimates in `agents/*.json` are planning figures, not measurements, so keep provider-side spend limits on as the real backstop. The [running guide](docs/RUNNING.md) covers the rest, including when a re-run costs money again.

## Contributing

Benchmark ideas are welcome, and the first way needs no code.

1. **Propose a benchmark.** Open a [proposal](../../issues/new?template=benchmark-proposal.yml) describing a task you watched an AI get wrong.
2. **Build an eval.** [CONTRIBUTING.md](CONTRIBUTING.md) has the anatomy and the authoring rules.

## Built on

A plain Java 26 harness with no framework dependencies and no agent SDK. It drives the agent CLIs headless in Docker, judges with Maven, and keeps every measurement-critical file under a content hash. Adding a CLI is one class, one services line, and one pinned install line in the Dockerfile.

The subject under test is [Spring Boot](https://spring.io/projects/spring-boot) and the [Spring portfolio](https://spring.io/projects); every fixture is a real Boot 4 project from [start.spring.io](https://start.spring.io). The agents under test are [Claude Code](https://claude.com/claude-code), [Codex](https://github.com/openai/codex), [Gemini CLI](https://github.com/google-gemini/gemini-cli), and [Qwen Code](https://github.com/QwenLM/qwen-code), pinned in the image.

Related but not a dependency: [Agent Bench](https://github.com/markpollack/agent-bench) by Mark Pollack benchmarks agents on enterprise Java workflows like issue triage and PR review. Spring Evals measures a different axis, whether a model knows the framework itself. Run both for the full picture.

## Roadmap

- The first campaign under harness 0.6.0: the full agent matrix across all sixteen evals at three samples.
- A hard tier. Three evals are hard today, and the next ones need diagnosis across several files rather than API recall.
- A closed-book track through an egress allowlist, so contamination is prevented instead of flagged.
- A controlled-model track through a Spring AI agent loop, so rows become model measurements instead of agent measurements.
- A private rotating holdout catalog, with retired tasks published for review.
- Gradle project variants.

## License

[Apache 2.0](LICENSE)
