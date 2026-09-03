# Getting started

Everything here is free except the last step, and that step tells you its price first.

## What you need

JDK 26 and Docker running. Every agent and every judge runs in a container, so nothing is installed on your machine and Docker is required even for the free parts. SDKMAN users can run `sdk env` in the repo root to pick up the pinned JDK.

Scored runs also need a credential for at least one agent. You do not need one to see the benchmark work.

## Run the wizard

```bash
./spring-evals serve
```

Open <http://localhost:4173/onboarding.html>. It binds to localhost only.

Six steps, in order:

1. **Environment.** Your JDK, whether Docker is reachable, and whether the benchmark image is built.
2. **Agents.** Pick the ones you have credentials for. Your choice is saved to `spring-evals.local.json`, so later commands skip the rest.
3. **Credentials.** Checks that each chosen agent's environment variables are set, as the container will see them. Values are never stored or shown. [Agent setup](AGENT_SETUP.md) covers how to get each one.
4. **Prove it.** Streams a real eval through the judge so you watch the broken project fail and the reference solution pass. First run builds the image, so give it several minutes.
5. **Estimate.** Projects what your first run will cost.
6. **Your run command.** Prints the exact command with the paid-run flags. Nothing on the page can start a paid run. You paste the command yourself.

Step 4 is the one that matters most. It proves the benchmark judges honestly before you spend anything on it.

## Your first scored run

This is the only step that costs money, and it should cost cents. Paid execution refuses to start without an explicit flag and a spend cap:

```bash
./spring-evals run --agent gemini-3-5-flash-lite --pilot \
  --samples 1 --run-name my-first-run --allow-paid-run --max-total-cost 1
```

That is three evals on a fast, cheap model, projected at about $0.15. Use whichever agent you set up in step 3; the fast tiers all land in the same range. Run `estimate` with the same flags first if you want the number before you commit.

When that works, the natural next step is the whole catalog on the same model, which is about $0.80:

```bash
./spring-evals estimate --agent gemini-3-5-flash-lite --samples 1
```

Save the frontier models for when you want a specific model's number. One pass over the catalog costs about $23 on Opus 5 against $0.80 on Flash Lite, and both run the same evals through the same judge.

One caveat worth knowing before you scale up. The campaign cap reserves each sample's estimate and stops before the cap is breached, but it cannot stop a single sample that costs far more than its estimate, because the harness only learns the real number after the sample finishes. Keep a spend limit set in your provider's dashboard as the real backstop.

## See what happened

```bash
./spring-evals report
./spring-evals serve
```

Open <http://localhost:4173>. Your run is in the Runs section by name. `results/runs/my-first-run.md` has the raw story: the outcome, whether the tests passed and the checks held, a summary of what the agent did, and the path to its full session.

## The same path in the terminal

The wizard is a front end over these. Every one is free.

| Step | Command |
|---|---|
| See the catalog | `./spring-evals list` |
| Check an agent's credentials | `./spring-evals doctor --agent claude-sonnet-5` |
| Prove one eval judges correctly | `./spring-evals validate boot/002-restclient-migration` |
| Project a cost | `./spring-evals estimate --agent claude-sonnet-5 --eval boot/000-initializr-parity --samples 1` |

Do not set up all twelve agents. Pick the CLI you already use.

## Where to go next

- Add more agents: [AGENT_SETUP.md](AGENT_SETUP.md)
- Run more, spend less, read transcripts: [RUNNING.md](RUNNING.md)
- What a leaderboard row means, and how the benchmark stays honest: [METHODOLOGY.md](METHODOLOGY.md)
- Build an eval: [CONTRIBUTING.md](../CONTRIBUTING.md)
