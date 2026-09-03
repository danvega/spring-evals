# Contributing to Spring Evals

Thanks for helping measure how well AI models write Spring code. There are two ways to contribute, and the first one requires no code at all.

## 1. Propose a benchmark (no code)

The best eval ideas come from real pain: a task you watched an AI assistant get wrong. Open a [benchmark proposal issue](../../issues/new?template=benchmark-proposal.yml) and describe:

- What the agent should be asked to do
- Why models get it wrong today (wrong package, outdated API, hallucinated config)
- How a hidden test could verify success

Maintainers and other contributors can pick up proposals and turn them into evals. If you want to build it yourself, say so in the issue and we will assign it to you.

## 2. Build an eval (PR)

### Anatomy

Evals live under a project suite directory. Every project on [spring.io/projects](https://spring.io/projects) has one under `evals/` (framework, boot, data, cloud, ai, security, batch, kafka, and so on; each suite README states its scope). Pick the suite your eval targets and take the next free number in it (`000`, `001`, ...).

```
evals/<project>/<nnn>-<eval-name>/
├── eval.yaml      # metadata
├── PROMPT.md      # the task, exactly as the agent receives it
├── project/       # the workspace the agent gets (complete Maven project with wrapper)
├── EVAL/          # hidden tests, injected after the agent finishes
│   ├── src/test/java/...
│   └── checks.json # optional idiom checks and pinned fixtures
├── SOLUTION/      # reference solution; validate requires it to reach pass
│   └── src/...    # replaces project/src entirely; optional pom.xml replaces the pom
├── ALTERNATIVES/  # optional: other legitimate solutions, same layout; each must reach pass
│   └── <name>/src/...
└── WORKAROUNDS/   # optional: pass the tests but miss the idiom; each must reach functional_only
    └── <name>/src/...
```

`eval.yaml` fields:

```yaml
name: 001-my-eval-name      # must match the directory name
project: boot               # must match the suite directory
title: One line, human readable
category: json | web | data | security | testing | config | observability | messaging
type: fix | build           # fix broken code, or build a feature from scratch
difficulty: easy | medium | hard
baseline_failure: compile_failure | test_failure
boot_version: 4.0.7
timeout_seconds: 900
description: >
  What this eval tests and why models fail it. This is documentation,
  the agent never sees it.
```

### Authoring rules

These rules keep the benchmark honest. PRs that break them will be asked to change.

1. **Prompts describe symptoms, not solutions.** Write the prompt the way a developer would describe the problem to a colleague. "The build fails after our Boot 4 upgrade" is good. "Migrate to tools.jackson" is the answer, not a prompt.
2. **Hidden tests are black-box.** Test through HTTP endpoints, the application context, or observable behavior. Never reference internal classes the agent might legitimately rename or restructure. The tests must compile against any reasonable solution.
3. **Close the cheese paths.** Ask yourself how an agent could pass without actually solving the task, then block it. Add prompt constraints (allowed) and test assertions (better) for those paths.
4. **Include a reference solution.** CI validates every eval: the broken project must fail the hidden tests for the declared `baseline_failure`, `SOLUTION/` and every `ALTERNATIVES/<name>/` must reach `pass`, and every `WORKAROUNDS/<name>/` must reach `functional_only`. All must hold before merge.
5. **One concept per eval.** An eval should test one thing a model gets wrong. If your prompt needs three unrelated fixes, split it into three evals.
6. **Pin versions.** Use a released Spring Boot version in the pom, never snapshots. Note it in `eval.yaml`.
7. **Keep it small.** Agents pay per token. A handful of source files is enough to make the task real.
8. **Check required mechanisms deterministically, and only what the prompt requires.** If the prompt requires a specific modern Spring API, add `EVAL/checks.json` with required or forbidden regex patterns, or a `requiredRuntimeArtifacts` list. Behavioral tests alone may accept a hand-written workaround. A check may only enforce something the prompt states; if a constraint matters, write it into the prompt the way a team would say it, and prefer a hidden test that observes the behavior over a regex that matches its spelling. Source patterns see comment-stripped Java and Kotlin, and pom patterns see the XML with comments dropped, so neither can be satisfied or tripped by a comment.
9. **Prove the checks with alternatives and workarounds.** For every check, add at least one `ALTERNATIVES/<name>/` that solves the task with a different legitimate Boot mechanism than `SOLUTION/` and still passes every check, and where the eval names a shortcut, a `WORKAROUNDS/<name>/` that passes the hidden tests and trips a check. Validate runs both gates, so a later edit to a check that starts rejecting a legitimate shape fails CI instead of silently rotting. A candidate that fails the hidden tests belongs in neither directory; the tests already reject it.
10. **Pin fixture files the agent must not touch.** If the prompt declares a test double or stub off-limits (for example a fake gateway the task must be solved around, not edited), list its workspace-relative path in a `pinned` array in `EVAL/checks.json`. The judge byte-compares every pinned file against `project/` before running tests and fails any modification or deletion as a `policy_failure`. Warning: never pin a file the task legitimately needs to edit; the reference solution would fail the same check and `validate` would reject the eval.

Example mechanism policy:

```json
{
  "requiredSourcePatterns": ["ProblemDetail"],
  "forbiddenSourcePatterns": ["@RequestHeader"],
  "requiredPomPatterns": ["spring-boot-starter-restclient"],
  "forbiddenPomPatterns": ["resilience4j"],
  "requiredRuntimeArtifacts": ["org.springframework.boot:spring-boot-h2console"],
  "pinned": ["src/main/java/com/example/FakePaymentGateway.java"]
}
```

`requiredRuntimeArtifacts` is confirmed on the candidate's resolved runtime classpath in the judge container, so a dependency declared at test scope does not count. Pom regexes cannot see scope, which is why this exists. Keep policies narrow and accept every reasonable framework-native solution. They supplement behavioral tests; they do not replace them. A miss on any idiom check with passing tests records `functional_only`, not a failure, and the leaderboard shows both numbers.

### Validate locally

```bash
./spring-evals validate boot/001-my-eval-name
```

This runs every gate: the baseline, `SOLUTION/`, each alternative, and each workaround. It needs a JDK 26+ and a running Docker daemon. The first run builds the benchmark image and downloads Maven dependencies, so give it several minutes.

If you have an agent CLI installed, also do a smoke run:

```bash
./spring-evals estimate --agent claude-sonnet-5 --eval boot/001-my-eval-name --samples 1
./spring-evals run --agent claude-sonnet-5 --eval boot/001-my-eval-name --samples 1 \
  --allow-paid-run --max-total-cost 2
```

### PR checklist

- [ ] `eval.yaml` complete, `name` matches the directory, `project` matches the suite
- [ ] Prompt is symptom-based and states the constraints a real team would have
- [ ] Hidden tests are black-box and have clear failure messages
- [ ] `SOLUTION/` included, plus an `ALTERNATIVES/` entry for every check and a `WORKAROUNDS/` entry for every named shortcut
- [ ] `./spring-evals validate <project>/<name>` passes locally
- [ ] Eval added to the table in README.md

## 3. Versioning (harness changes)

The benchmark version string (for example `0.3.0+11b0497585a6`) is part of result identity. When it changes, cached results leave the leaderboard cohort. Recovering them means paid re-runs. So version changes follow strict rules:

- **Results with the same full version string are comparable.** That is what the version means. Nothing else.
- **Bumps happen only in harness batches that change measurement behavior.** Isolation, judging, scoring, workspace handling. Exactly one bump per batch.
- **Never bump for docs, dashboard, eval-content, or toolchain changes.** Eval edits already rotate that eval's own content hash. Docs and dashboard are not part of measurement. The Dockerfile (JDK, Node, CLI pins) is toolchain: bumping a pin is routine, each row records the toolchain it ran on, and the affected agents' rows are rebuilt on their next run.
- **Do not touch the hashed harness files outside a declared bump batch.** The hash suffix covers `Main.java`, `Agents.java`, `Workspaces.java`, `MavenJudge.java`, `EvalDefinition.java`, `DockerSandbox.java`, `RunScheduler.java`, `Judgment.java`, `JavaComments.java`, `RuntimeArtifacts.java`, every file under `cli/` (one `AgentCli` implementation per agent CLI), `harness/pom.xml`, and `spring-evals`. A changed suffix under the same prefix means undeclared measurement drift, and the 0.2.0 era shows why that hurts.
- **Every bump adds its row to [docs/VERSIONS.md](docs/VERSIONS.md) in the same commit** that changes the constant in `ContentHashes.java`.

Current: 0.6.0 (outcome model, samples, alternatives gate, transcripts, docker only). The measurement core is frozen again: a version bump requires a measurement-correctness reason, and 1.0.0 is declared only after the full matrix has run at least twice under one unchanged version (minor versions are unbounded, so there is no pressure to ever reach it). Toolchain refreshes do not count as changes for that rule.

## Questions

Open a discussion or ping [@danvega](https://github.com/danvega).
