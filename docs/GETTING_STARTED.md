# Getting started

Everything here runs free except the last step, and that one tells you its price first. Total time: about ten minutes.

## What this is, in three sentences

Spring Evals measures how well AI models and coding agents write real Spring code. Each eval is a real Spring Boot project with a task described the way a developer would describe it; the agent works in an isolated workspace, and hidden tests it never sees decide pass or fail. Results feed a leaderboard and a local dashboard.

## 1. Check the basics

You need JDK 25+ and network access for Maven. That is all for the free parts. SDKMAN users can just run `sdk env` in the repo root; the checked-in `.sdkmanrc` pins a matching JDK.

```bash
./spring-evals list
```

You should see the eval catalog, grouped by Spring project.

## 2. Prove the benchmark works, for free

```bash
./spring-evals validate boot/002-restclient-migration
```

This takes one eval and proves both of its gates: the broken project fails the hidden tests, and the reference solution passes them. First run downloads Maven dependencies, so give it a few minutes. No AI is involved and nothing is spent.

## 3. Pick one agent you already have

Do not set up all twelve agents. Pick the one CLI you already use, set it up from the matching section of the [agent setup guide](AGENT_SETUP.md), and verify it:

```bash
./spring-evals doctor --agent claude-sonnet-5
```

Doctor checks the CLI, credentials, and billing source without sending a prompt or spending anything. Fix what it flags; ignore agents you did not set up. If you want `--all-agents` to skip the ones you have no keys for, set `"enabled": false` in their `agents/<name>.json`.

## 4. See the price before you spend

```bash
./spring-evals estimate --agent claude-sonnet-5 --eval boot/000-initializr-parity --attempts 1
```

One agent, one eval, one attempt is nearly always the right first run. Expect an estimate around a dollar or two.

## 5. Your first scored run

Paid execution refuses to start without an explicit flag and a spend cap:

```bash
./spring-evals run --agent claude-sonnet-5 --eval boot/000-initializr-parity \
  --attempts 1 --run-name my-first-run --allow-paid-run --max-total-cost 2
```

The eval asks the model to build a new Spring Boot 4 project from an empty repository, judged against what start.spring.io produces today.

## 6. See what happened

```bash
./spring-evals report
./spring-evals serve
```

Open http://localhost:4173. Your run appears in the Runs section by name, with a scoreboard and per-attempt detail. The raw story lives in `results/runs/my-first-run.md`, including the agent's own summary of what it did.

## Where to go next

- Add more agents: [AGENT_SETUP.md](AGENT_SETUP.md)
- Run more, spend less: [RUNNING.md](RUNNING.md)
- What a leaderboard row means and how the benchmark stays honest: [METHODOLOGY.md](METHODOLOGY.md)
- Build an eval of your own: [CONTRIBUTING.md](../CONTRIBUTING.md)
