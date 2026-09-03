# Running the benchmark

How to run evals without spending more than you intend, and how to keep results comparable over time. Agent installation and API keys are covered in [AGENT_SETUP.md](AGENT_SETUP.md).

## Selecting what runs

`run` and `estimate` accept the same selectors, and they combine freely (`doctor` takes only `--agent`, `--family`, or nothing for all agents):

```bash
--agent a[,b,c]          # explicit picks
--family claude          # name-prefix match: claude-*, codex-*
--all-agents             # every enabled agent in agents/
--eval boot/000-initializr-parity
--project boot           # one suite
--difficulty easy,medium
--pilot                  # the designated three-eval pilot subset
--samples 3              # independent samples per (agent, eval) cell; default 3, max 10
--parallel 4             # run only: max concurrent containers, one lane per agent CLI (default 4, max 8)
--run-name my-baseline   # names the run; omit for a generated name like eager-bean-42
```

To keep agents defined but out of `--all-agents`, `--family`, and selector-less `estimate`, copy `spring-evals.local.json.example` to a gitignored `spring-evals.local.json` at the repo root and list the agents you actually run:

```json
{
  "enabledAgents": ["claude-sonnet-5", "codex-gpt-5-6-luna"]
}
```

An absent file or an absent `enabledAgents` key enables every agent. Naming an agent explicitly with `--agent` still runs it (with a printed note), and `doctor` still inspects every agent while annotating the excluded ones. This keeps the full matrix on disk while only paying for the agents you have keys for. Selection changes which agents commands pick up, never how a sample is measured, so it is not part of result identity.

## Estimate first, always

`estimate` is free and takes the same selectors as `run`:

```bash
./spring-evals estimate --all-agents --samples 1
./spring-evals estimate --agent claude-sonnet-5 --project boot
./spring-evals estimate --all-agents --pilot --samples 3
```

Estimates come from `estCostPerAttemptUsd` in each agent config (one sample is one attempt of the task). Every sample runs, so the projection is evals times samples times that figure. Claude Code reports the actual cost per sample automatically; for other CLIs, check the provider console after a run and adjust the config.

## The paid-run lock

`run` refuses paid execution unless both flags are present:

```bash
./spring-evals run --agent claude-sonnet-5 --pilot --samples 1 \
  --allow-paid-run --max-total-cost 2.00
```

Before each sample the harness reserves that agent's configured estimate and stops before the campaign cap would be exceeded. This is an estimated reservation cap, not a provider billing guarantee. Agents without a configured estimate are refused entirely. Keep provider-side spend limits in place as the real backstop, and keep `--max-total-cost` below them.

## Parallel execution

`run` executes one lane per agent CLI (claude, codex, gemini, qwen-code) concurrently while samples inside each lane stay strictly serial. The lane split follows the rate limits: provider limits apply per account, so two claude samples at once fight over one account's quota, while a claude and a codex sample do not. `--parallel <n>` caps concurrent containers; the default is 4 and the maximum is 8.

```bash
./spring-evals run --all-agents --pilot --samples 1 --parallel 4 \
  --allow-paid-run --max-total-cost 10
```

Reservations against the campaign cap are atomic across lanes, and skip/`--force` semantics are identical to a serial run. One caveat: an actual cost above its estimate counts against the cap only when that sample finishes, so concurrent in-flight overruns can edge past `--max-total-cost`. It remains an estimated reservation cap, not a billing guarantee; keep provider-side limits in place.

Superseded content-addressed `spring-evals-bench` images accumulate as the Dockerfile changes; `docker image prune` or a manual `docker rmi` reclaims the space.

## A cost-control playbook

- Start with `--pilot --samples 1`, then expand only the rows that earn it.
- Smoke-test a new agent with `--difficulty easy` before a full run.
- Scope with `--eval` or `--project` while iterating on anything.
- Samples multiply spend linearly. One sample per cell is a smoke test; three is the leaderboard default because a single pass or fail per cell is a coin flip.
- Per-sample budget caps cannot be passed to a headless CLI inside a container. The campaign cap works from per-sample estimates plus claude-reported actual costs. Set per-model limits in provider dashboards as the real backstop.
- Every eval project builds with `-ntp`, so agents do not burn tokens reading Maven download logs.

## Memoization and when you pay again

Results accumulate in `results/results.json`. A result's cache identity includes the eval content hash, the agent config hash, the harness version, the CLI version, and the JDK and OS. A later run skips any cell that already holds the requested number of samples under the same identity, and tops up cells that hold fewer, so expanding a campaign only pays for what is new.

The flip side: editing an eval, changing an agent config, upgrading a CLI, or changing the harness invalidates the matching cached results on purpose. Passing `--force` reruns the current identity and replaces those records.

Recorded spend appears in the report output, `results/leaderboard.md`, and the dashboard's Benchmark spend tile.

## After a run

1. `./spring-evals report` refreshes `results/leaderboard.md`, `dashboard/data.json`, and a per-run log at `results/runs/<run-name>.md` with per-sample detail (outcome, tests, idiom) and each agent's closing summary.
2. Write a plain-language findings summary to `results/runs/<run-name>.notes.md`: what the run tested, real verdicts versus infrastructure failures, and what to fix. Re-run `report` and it merges into the run log and the dashboard's run drill-down.
3. `./spring-evals serve` and open http://localhost:4173. The run appears in the Runs section by name.
4. Check provider consoles for actual spend on API-billed agents, and tune `estCostPerAttemptUsd` in agent configs as real numbers come in.

## New model day

A new model dropped and claimed the crown. To get a comparable row:

```bash
# 1. describe it (one JSON file)
cat > agents/new-hotness.json <<'JSON'
{
  "name": "new-hotness",
  "provider": "claude",
  "model": "the-new-model-id",
  "env": { "CLAUDE_CODE_OAUTH_TOKEN": "${CLAUDE_BENCH_OAUTH_TOKEN}" },
  "estCostPerAttemptUsd": 1.00
}
JSON

# 2. readiness and price, both free
./spring-evals doctor --agent new-hotness
./spring-evals estimate --agent new-hotness

# 3. cheap first pass, then the full treatment if it earns it
./spring-evals run --agent new-hotness --pilot --samples 1 --allow-paid-run --max-total-cost 5
./spring-evals run --agent new-hotness --allow-paid-run --max-total-cost 40

# 4. refresh the leaderboard and dashboard
./spring-evals report
```

Every existing row stays comparable because the evals did not change under it.

Supported providers today: `claude`, `codex`, `gemini`, and `qwen-code`. A new model on an existing provider is just another JSON file. A new agent CLI is one `AgentCli` implementation under `harness/src/main/java/dev/danvega/springevals/cli/` (headless command, seeded files, output parsing, doctor checks), one line in `META-INF/services`, and its pinned install line in `harness/docker/Dockerfile`. The `env` map in a config is the only host state passed into the sample's container, and values can reference host environment variables with `${VAR}`.

## Open and local models

Frontier models are only half the story. The same harness runs open-weight models by pointing an agent CLI at a compatible endpoint: Kimi K3 through Moonshot's Anthropic-compatible endpoint, Grok 4.5 through xAI's OpenAI-compatible endpoint, and anything Ollama serves on the host through the Qwen Code CLI. Local servers are addressed as `host.docker.internal`, never `localhost`, because inside the container localhost is the container. Setup for each is in [AGENT_SETUP.md](AGENT_SETUP.md).

Cost for local models reports as n/a. Duration still comes through, which is half the story for local models anyway.
