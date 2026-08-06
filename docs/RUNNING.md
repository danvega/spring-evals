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
--attempts 1             # default is 1; retries are explicit
--parallel 4             # run only, docker mode only: max concurrent containers (default 4, max 8)
--run-name my-baseline   # names the run; omit for a generated name like eager-bean-42
```

To keep agents defined but out of `--all-agents`, `--family`, and selector-less `estimate`, copy `spring-evals.local.json.example` to a gitignored `spring-evals.local.json` at the repo root and list the agents you actually run:

```json
{
  "enabledAgents": ["claude-sonnet-5", "codex-gpt-5-6-luna"]
}
```

An absent file or an absent `enabledAgents` key enables every agent. Naming an agent explicitly with `--agent` still runs it (with a printed note), and `doctor` still inspects every agent while annotating the excluded ones. This keeps the full matrix on disk while only paying for the agents you have keys for. Selection changes which agents commands pick up, never how an attempt is measured, so it is not part of result identity.

## Estimate first, always

`estimate` is free and takes the same selectors as `run`:

```bash
./spring-evals estimate --all-agents --attempts 1
./spring-evals estimate --agent claude-sonnet-5 --project boot
./spring-evals estimate --all-agents --pilot --attempts 1
```

Estimates come from `estCostPerAttemptUsd` in each agent config. Treat them as planning numbers and tune them as real spend data comes in. Claude Code reports the actual cost per attempt automatically; for other CLIs, check the provider console after a run and adjust the config.

## The paid-run lock

`run` refuses paid execution unless both flags are present:

```bash
./spring-evals run --agent claude-sonnet-5 --pilot --attempts 1 \
  --allow-paid-run --max-total-cost 2.00
```

Before each attempt the harness reserves that agent's configured estimate and stops before the campaign cap would be exceeded. This is an estimated reservation cap, not a provider billing guarantee. Agents without a configured estimate are refused entirely. Keep provider-side spend limits in place as the real backstop, and keep `--max-total-cost` below them.

## Parallel execution

In docker sandbox mode, `run` executes provider lanes (claude, codex, gemini, qwen-code) concurrently while attempts inside each lane stay strictly serial. The lane split follows the rate limits: provider limits apply per account, so two claude attempts at once fight over one account's quota, while a claude and a codex attempt do not. `--parallel <n>` caps concurrent containers; the default is 4 and the maximum is 8.

```bash
./spring-evals run --all-agents --pilot --attempts 1 --parallel 4 \
  --allow-paid-run --max-total-cost 10
```

Host mode always runs serial, and passing `--parallel` with `--sandbox host` is refused: host isolation (EnvSandbox) mutates the one shared process environment per attempt, which concurrent attempts cannot share. Reservations against the campaign cap are atomic across lanes, and skip/`--force` semantics are identical to a serial run. One caveat: an actual cost above its estimate counts against the cap only when that attempt finishes, so concurrent in-flight overruns can edge past `--max-total-cost`. It remains an estimated reservation cap, not a billing guarantee; keep provider-side limits in place.

Superseded content-addressed `spring-evals-bench` images accumulate as the Dockerfile changes; `docker image prune` or a manual `docker rmi` reclaims the space.

## A cost-control playbook

- Start with `--pilot --attempts 1`, then expand only the rows that earn it.
- Smoke-test a new agent with `--difficulty easy` before a full run.
- Scope with `--eval` or `--project` while iterating on anything.
- Single attempts keep expected cost equal to worst case. Retries are where spend concentrates.
- Claude-family configs carry a per-attempt `budgetUsd` cap that host mode passes to the CLI. Docker mode cannot pass it (the CLI runs headless in a container); there the campaign cap works from per-attempt estimates plus claude-reported actual costs, and the run prints a note saying so. Set per-model limits in provider dashboards either way.
- Every eval project builds with `-ntp`, so agents do not burn tokens reading Maven download logs.

## Memoization and when you pay again

Results accumulate in `results/results.json`. A result's cache identity includes the eval content hash, the agent config hash, the harness version, the CLI version, and the JDK and OS. A later run skips anything that already passed or exhausted its attempts under the same identity, so expanding a campaign only pays for what is new.

The flip side: editing an eval, changing an agent config, upgrading a CLI, or changing the harness invalidates the matching cached results on purpose. Passing `--force` reruns the current identity and replaces those records.

Recorded spend appears in the report output, `results/leaderboard.md`, and the dashboard's Benchmark spend tile.

## After a run

1. `./spring-evals report` refreshes `results/leaderboard.md`, `dashboard/data.json`, and a per-run log at `results/runs/<run-name>.md` with attempt-level detail and each agent's closing summary.
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
  "budgetUsd": 3.00,
  "estCostPerAttemptUsd": 1.00
}
JSON

# 2. readiness and price, both free
./spring-evals doctor --agent new-hotness
./spring-evals estimate --agent new-hotness

# 3. cheap first pass, then the full treatment if it earns it
./spring-evals run --agent new-hotness --pilot --attempts 1 --allow-paid-run --max-total-cost 5
./spring-evals run --agent new-hotness --attempts 1 --allow-paid-run --max-total-cost 15

# 4. refresh the leaderboard and dashboard
./spring-evals report
```

Every existing row stays comparable because the evals did not change under it.

Supported providers today: `claude`, `codex`, `gemini`, and `qwen-code`. A new model on an existing provider is just another JSON file. A new provider is one more case in the harness's `Agents.java`, or a custom `AgentModel` for CLIs the Agent Client does not cover yet. The optional `env` map in a config is passed to the agent CLI process, and values can reference host environment variables with `${VAR}`.

## Open and local models

Frontier models are only half the story. The same harness runs open-weight models by pointing an agent CLI at a compatible endpoint: Kimi K3 through Moonshot's Anthropic-compatible endpoint, Grok 4.5 through xAI's OpenAI-compatible endpoint, and anything Ollama serves locally through the Qwen Code CLI. Setup for each is in [AGENT_SETUP.md](AGENT_SETUP.md).

Cost for local models reports as n/a. Duration still comes through, which is half the story for local models anyway.
