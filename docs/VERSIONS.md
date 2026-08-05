# Measurement Versions

## What the version string means

Every result records a benchmark version like `0.3.0+11b0497585a6`. It comes from `ContentHashes.benchmark()` in `harness/src/main/java/dev/danvega/springevals/ContentHashes.java`.

The string has two parts:

- The prefix (`0.3.0`) is declared by hand. It is bumped only when a harness batch changes measurement behavior.
- The suffix is the first 12 characters of a SHA-256 hash over the measurement-critical harness files: `Main.java`, `Agents.java`, `Workspaces.java`, `MavenJudge.java`, `EvalDefinition.java`, `EnvSandbox.java`, `DockerSandbox.java`, `harness/docker/Dockerfile`, `harness/pom.xml`, and the `spring-evals` wrapper.

The meaning of the version is simple. Results with the same full version string are comparable. The leaderboard cohort is keyed on the full string. When the string changes, cached results leave the cohort. They stay in run history, but getting them back on the leaderboard costs a paid re-run. That is why version changes must be rare and deliberate.

## The +hash suffix

The same prefix with a different suffix means a hashed file changed without a declared bump. That is undeclared measurement drift, and the suffix exists to make it visible.

It happened during the 0.2.0 era. The hashed harness files changed four times without a prefix bump (five distinct suffix values over the era), and runs landed under two different full strings: first-light under `0.2.0+9e4deb544cd1` and second-light under `0.2.0+87a8d7e9d450`. Under the rule above these two runs are separate cohorts, even though both say 0.2.0.

The policy in [CONTRIBUTING.md](../CONTRIBUTING.md#3-versioning-harness-changes) forbids this going forward. Hashed files change only in a batch that also bumps the prefix, so the prefix and suffix always move together.

## Version history

| Version | Introduced | What changed | Why it affects measurement | Runs recorded under it | Trust notes |
|---|---|---|---|---|---|
| 0.2.0 | `cfd4fbc` (2026-08-04, initial commit) | First working harness: workspaces, agent adapters, Maven judge, hidden tests, mechanism checks. | Baseline. Defined the first judging pipeline. | [first-light](../results/runs/first-light.md) (`0.2.0+9e4deb544cd1`), [second-light](../results/runs/second-light.md) (`0.2.0+87a8d7e9d450`) | Claude-family results are contaminated. The agent SDK silently dropped per-agent environment settings (a dead store, verified by bytecode inspection), so the sterile config dir never reached the Claude CLI. Claude attempts ran with the host config visible, including installed Spring skills. Do not publish Claude verdicts from this era. Infrastructure findings for the other CLIs remain valid. Also note the two hash suffixes: hashed files changed mid-era with no declared bump, which the current policy forbids. |
| 0.3.0 | `16ee46f` (2026-08-05) | EnvSandbox: the harness mutates its own process environment around each attempt, with a self-test before any paid spend. Claude attempts get a fresh empty CLAUDE_CONFIG_DIR and stripped host ANTHROPIC*/CLAUDE* variables. Same batch: SERVER_PORT=0 per attempt, and zero-change fast-fail so a CLI that fails with a clean exit records `agent_error` instead of a fake 0%. | Agents can no longer see host credentials, host CLAUDE.md, skills, or MCP servers, so verdicts measure the model. Failed CLIs can no longer be scored as model failures. | [eager-boot-85](../results/runs/eager-boot-85.md), [sturdy-mono-00](../results/runs/sturdy-mono-00.md) (both `0.3.0+11b0497585a6`) | First trustworthy Claude-family results. Isolation was verified in-run: sterile config dirs were populated and the host-login warnings disappeared. sturdy-mono-00 replicated the eager-boot-85 verdicts exactly, the first cross-run consistency signal. |

| 0.4.0 | 05fd6dc (2026-08-05) | Docker sandbox mode, default when Docker is present (`--sandbox docker\|host`). The agent CLI runs headless, non-root, in a container from one pinned benchmark image (`harness/docker/Dockerfile`, digest-pinned base, image tag content-addressed from the Dockerfile). The judge's `./mvnw clean test` runs in a separate fresh container from the same image, started after the agent container is destroyed, with an empty Maven overlay over the read-only host cache mounts. Same batch: pom policy patterns are applied to an XML-parsed rendering of the pom (comments dropped as nodes, CDATA coalesced) instead of the raw text. The hash input list grew by `DockerSandbox.java` and the Dockerfile. | Agent and judge share one pinned toolchain, so verdicts stop depending on host toolchain drift. Codex, Gemini CLI, and Qwen Code no longer see global context files in docker mode, extending the 0.3.0 Claude-only guarantee to all four CLIs (CODEX_HOME is seeded with only auth.json). The fresh judge container means agent-planted processes, `~/.m2/settings.xml`, poisoned local artifacts, or a tampered Maven distribution cannot survive into judging. The XML-parsed pom text means a commented-out dependency cannot satisfy a required mechanism check, and CDATA-spliced fake comment markers cannot hide forbidden configuration from it. | none yet | Docker-mode records carry a `docker:` prefix on both the CLI version and the Java version (the container JDK), so host- and docker-mode records are never cache-equivalent and the mode is visible per record. Claude docker records include cost and tokens parsed from the CLI's headless JSON; codex, gemini, and qwen-code docker records carry null there (their CLIs expose none headlessly). Claude's per-attempt budgetUsd cap is not enforced in docker mode (estimate-based campaign cap plus claude-reported actuals instead). Host mode keeps the 0.3.0 behavior and trust profile. Free verification covered image, containers, all four headless invocations (credential-less, auth-only failures), and the full judge pipeline in containers; the first paid docker-mode run is still pending, so treat the docker agent path as unproven against a live model until one lands. Hash suffix finalized by a comment-only tightening pass immediately after the batch; no results were recorded under the interim suffix. |

## Planned

- 1.0.0: a deliberate freeze after container isolation stabilizes. From 1.0.0 on, the leaderboard cohort is expected to stay stable for long stretches.

Each future bump adds a row to this table in the same commit that changes the version constant.
