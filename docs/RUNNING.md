# Running the benchmark

How to run evals without spending more than you intend, and how to keep results comparable over time. Agent installation and API keys are covered in [AGENT_SETUP.md](AGENT_SETUP.md). If you have not run anything yet, the onboarding wizard in [GETTING_STARTED.md](GETTING_STARTED.md) walks the first run in the browser.

## Selecting what runs

`run` and `estimate` accept the same selectors, and they combine freely:

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
--run-name my-baseline   # run only: names the run; omit for a generated name like eager-bean-42
--force                  # run only: rerun cells that already hold results under the current identity
```

`doctor` takes `--agent` or `--family` (or nothing, for every agent) plus two switches of its own. `--docker` builds the image if needed and probes every CLI inside it. `--json` prints the same report as one JSON document for scripts: `status`, `billing`, `estimate`, and `findings` per agent, plus a summary. `validate` takes eval ids as plain arguments and runs every eval when given none.

To keep agents defined but out of `--all-agents`, `--family`, and selector-less `estimate`, copy `spring-evals.local.json.example` to a gitignored `spring-evals.local.json` at the repo root and list the agents you actually run:

```json
{
  "enabledAgents": ["claude-sonnet-5", "codex-gpt-5-6-luna"]
}
```

An absent file or an absent `enabledAgents` key enables every agent. Naming an agent explicitly with `--agent` still runs it (with a printed note), and `doctor` still inspects every agent while annotating the excluded ones. This keeps the full matrix on disk while only paying for the agents you have keys for. The onboarding wizard's Agents step writes the same file. Selection changes which agents commands pick up, never how a sample is measured, so it is not part of result identity.

## Estimate first, always

`estimate` is free and takes the same selectors as `run`:

```bash
./spring-evals estimate --all-agents --samples 1
./spring-evals estimate --agent claude-sonnet-5 --project boot
./spring-evals estimate --all-agents --pilot --samples 3
```

Estimates come from `estCostPerAttemptUsd` in each agent config. The key name predates `--samples`; it is the estimate for one sample. Every sample runs, so the projection is evals times samples times that figure. Claude Code reports the actual cost per sample automatically; for other CLIs, check the provider console after a run and adjust the config.

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

Results accumulate in `results/results.json`. A result's cache identity includes the eval content hash, the agent config hash, the harness version, the CLI version, and the JDK and OS. A later run skips any cell that already holds the requested number of verdict samples under the same identity, and tops up cells that hold fewer by appending new samples. Infrastructure failures (`agent_error`, `judge_error`) never fill a cell, so a rerun after a fix adds the missing verdicts without `--force`. Expanding a campaign only pays for what is new.

The flip side: editing an eval, changing an agent config, upgrading a CLI, or changing the harness invalidates the matching cached results on purpose. Passing `--force` reruns the current identity and replaces those records.

Recorded spend appears in the report output, `results/leaderboard.md`, and the dashboard's Benchmark spend tile.

## Transcripts and contamination flags

Every sample keeps the CLI's own event stream: Claude Code, Gemini CLI, and Qwen Code as `stream-json`, Codex as its JSONL event log. The file lives outside the repository, next to the workspaces:

```
$TMPDIR/spring-evals-runs/transcripts/<run-name>/<agent>/<project>-<nnn>-<name>-s<sample>.jsonl
```

`SPRING_EVALS_RUNS_DIR` overrides the root. Transcripts survive until the OS cleans the temp directory, so copy the ones worth keeping.

The result record never copies session text. It carries the path and a summary of counts, which the run log prints on one line per sample:

```
- transcript: 14 commands, 6 files written, 2 URLs fetched (hosts: docs.spring.io, repo1.maven.org); raw at .../spring-evals-runs/transcripts/my-run/claude-sonnet-5/boot-002-restclient-migration-s1.jsonl
```

Read it as: how many shell commands the agent ran, how many file writes or edits it made, how many URLs it fetched, and which hosts those URLs pointed at. A CLI whose stream carries no structure reports zeros, never a failure. Open the raw file when an outcome surprises you. It is the agent's full session: every tool call, every command, and the final message.

After the agent container is destroyed and before judging, the harness scans the transcript for anything the agent should never have seen: the benchmark repository (`danvega/spring-evals`, or `spring-evals` on its own), the eval id or its directory name, and the `SOLUTION/`, `EVAL/`, `ALTERNATIVES/`, and `WORKAROUNDS/` directories. A hit becomes a contamination flag on the sample. The flag means the agent's session mentioned the benchmark, the task's identity, or a hidden directory. That is strong evidence it searched for or read the answer instead of solving the task. The flag never changes the verdict. Flagged samples appear in the run log as `CONTAMINATION FLAGS (verdict kept, exclusion is a human call)`, in `results/leaderboard.md`, and on the dashboard with a warning marker. Deciding whether to exclude them is your call, made in the run's findings notes. The scan is a word-boundary text match over what the CLI chose to emit, so a quieter CLI shows less, and an agent that fetched the answer without naming the source would not be caught. An egress allowlist is the stronger control and is on the roadmap.

## After a run

1. `./spring-evals report` refreshes `results/leaderboard.md`, `dashboard/data.json`, and a per-run log at `results/runs/<run-name>.md` with per-sample detail (outcome, tests, idiom, transcript summary, flags) and each agent's closing summary.
2. Write a plain-language findings summary to `results/runs/<run-name>.notes.md`: what the run tested, real verdicts versus infrastructure failures, what to fix, and what you decided about any contamination-flagged samples. Re-run `report` and it merges into the run log and the dashboard's run drill-down.
3. Read transcripts where the outcome surprises you. The section above says where they are and how to read the summary line.
4. `./spring-evals serve` and open http://localhost:4173. The run appears in the Runs section by name.
5. Check provider consoles for actual spend on API-billed agents, and tune `estCostPerAttemptUsd` in agent configs as real numbers come in.

## The dashboard server

`./spring-evals serve` serves `dashboard/` with the JDK's built-in file server on port 4173 (`--port` changes it) and adds the small JSON API behind `onboarding.html`. It binds to loopback only and answers only to a localhost `Host` header, because that API writes `spring-evals.local.json` and launches `validate`. Nothing it exposes can start a paid run. To publish the dashboard, push to `main`: the Pages workflow uploads `dashboard/` as static files, and the onboarding page detects static hosting and stays read-only.

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
./spring-evals run --agent new-hotness --allow-paid-run --max-total-cost 60

# 4. refresh the leaderboard and dashboard
./spring-evals report
```

Every existing row stays comparable because the evals did not change under it.

Supported providers today: `claude`, `codex`, `gemini`, and `qwen-code`. A new model on an existing provider is just another JSON file. A new agent CLI is one `AgentCli` implementation under `harness/src/main/java/dev/danvega/springevals/cli/` (headless command, seeded files, output parsing, transcript summary, doctor checks), one line in `META-INF/services`, and its pinned install line in `harness/docker/Dockerfile`. The `env` map in a config is the only host state passed into the sample's container, and values can reference host environment variables with `${VAR}`.

## Open and local models

Frontier models are only half the story. The same harness runs open-weight models by pointing an agent CLI at a compatible endpoint: Kimi K3 through Moonshot's Anthropic-compatible endpoint, Grok 4.5 through xAI's OpenAI-compatible endpoint, and anything Ollama serves on the host through the Qwen Code CLI. Local servers are addressed as `host.docker.internal`, never `localhost`, because inside the container localhost is the container. Setup for each is in [AGENT_SETUP.md](AGENT_SETUP.md).

Cost for local models reports as n/a. Duration still comes through, which is half the story for local models anyway.
