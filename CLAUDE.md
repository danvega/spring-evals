# Spring Evals

A benchmark that measures how well AI models and coding agents write real Spring code. Coding agents solve tasks in real Spring Boot 4 projects inside isolated workspaces. Hidden JUnit tests and deterministic mechanism checks they never see decide pass or fail. Results feed a leaderboard and a dashboard.

## Layout

- `evals/<project>/<nnn>-<name>/` — one eval per directory, suites mirror spring.io/projects. Each eval: `PROMPT.md` (symptom-based task), `project/` (workspace the agent gets), `EVAL/` (hidden tests + optional `checks.json`), `SOLUTION/` (reference fix, CI-only), `eval.yaml` (metadata).
- `harness/` — plain Java 25 Maven app on the Spring AI Community Agent Client. Entry point is the `./spring-evals` wrapper at repo root.
- `agents/*.json` — one (CLI, model) pair per file. `dashboard/` — static results page. `docs/` — GETTING_STARTED.md (newcomer path), METHODOLOGY.md (what a row means), AGENT_SETUP.md (platform signup, keys, CLIs), RUNNING.md (cost control and run workflows).

## Commands

```bash
./spring-evals list                      # all evals
./spring-evals validate [evalId...]      # eval gates, no model calls, free
./spring-evals doctor [--family x]       # agent CLI readiness, no model calls, free
./spring-evals estimate [--agent x]      # cost projection, free
./spring-evals report                    # rebuild leaderboard + dashboard data
cd harness && ./mvnw test                # harness unit tests
./spring-evals serve                     # dashboard at localhost:4173, JDK file server
```

`./spring-evals run` executes real agents and **spends real money**. It is locked behind `--allow-paid-run --max-total-cost <usd>`. Never run it, and never add those flags, without the human explicitly asking for a paid run in this conversation.

## Definition of done

Work is done only when the matching verification passes AND an independent review has run. Run both yourself; do not declare success without them.

**Independent review**: after your change is complete and its verification is green, launch the `change-reviewer` subagent (defined in `.claude/agents/change-reviewer.md`) on your diff. It re-runs the free gates and hunts for the defects that matter here: solution leakage in prompts, cheese paths, overbroad mechanism checks, and weakened measurement integrity. Address every CONFIRMED finding before declaring done, and include the reviewer's verdict in your summary. The reviewer reports; you fix.

- **Eval added or changed**: `./spring-evals validate <project>/<nnn>-<name>` is green. That means structural metadata checks pass, the broken project fails for exactly the `baseline_failure` declared in `eval.yaml`, and `SOLUTION/` passes the hidden tests plus every `checks.json` mechanism check. Also update the suite README table and the root README eval table.
- **Harness changed**: `cd harness && ./mvnw test` is green AND a full `./spring-evals validate` is green. Validate is the end-to-end exercise of the judging pipeline without model calls.
- **Dashboard changed**: serve it, load it, zero console errors, verify in light and dark, and confirm it renders both the sample `data.json` and a missing-field-tolerant shape (real runs omit fields).
- **Docs changed**: links resolve; commands shown actually exist in `./spring-evals` usage.

## Rules for autonomous and parallel work

- Paid runs are human-only decisions. Everything else (validate, doctor, estimate, tests, dashboard) is free and safe to run.
- After a paid run completes, write a plain-language findings summary to `results/runs/<run-name>.notes.md` (what the run tested, verdicts vs infrastructure failures, and what to fix), then re-run `./spring-evals report`. The notes merge into the run log's Findings section and survive regeneration. Distinguish "the model failed the task" from "the harness or CLI failed" every time.
- Eval directories are independent. Parallel agents should each own distinct eval directories. The collision point is the `<nnn>` number: claim the next free number in the suite before building, and re-check before committing.
- `results/results.json` is harness-owned shared state. Never hand-edit it. Note that `run --force` replaces matching records, so it is not a complete historical spend ledger.
- Editing any file under an eval (prompt, project, tests, checks) changes its content hash and intentionally invalidates cached results for that eval. That is correct behavior, not a bug, but it means re-running costs money. Do not touch shipped evals casually.
- Agent workspaces live outside the repo (`$TMPDIR/spring-evals-runs`, override with `SPRING_EVALS_RUNS_DIR`). Nothing in the repo should reference workspace paths.
- Authoring rules for evals (symptom-based prompts, black-box tests, cheese-path closure, reference solutions) live in CONTRIBUTING.md and are binding.
- Comments state constraints the code cannot show. No narration, no change-history notes, no reviewer-directed justifications.

## Host context isolation

Benchmark validity depends on agents not seeing host context. Since harness 0.4.0 the default is docker sandbox mode when Docker is present: agent and judge run in fresh containers from one pinned image, and no host config or credentials reach them beyond the agent config env (plus, for codex only, a seeded auth.json). Host mode is the fallback and enforces per-attempt environment isolation (EnvSandbox) with a self-test before any spend: Claude runs get a fresh empty CLAUDE_CONFIG_DIR and stripped host ANTHROPIC*/CLAUDE* vars (never relax this; authenticate via CLAUDE_CODE_OAUTH_TOKEN from `claude setup-token` for subscription billing, or ANTHROPIC_API_KEY for metered API). Fresh workspaces are stripped of agent context files, and `doctor` warns about global context files for the other CLIs in host mode. Details in docs/METHODOLOGY.md under Host context isolation.
