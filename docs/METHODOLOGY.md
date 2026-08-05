# Benchmark Methodology

Spring Evals separates correctness, framework competency, agent performance, and cost instead of collapsing them into one unexplained score.

## What a row means

The current **agent track** measures a complete configuration: model, coding-agent CLI, CLI system prompt, tools, permissions, time limit, and environment. Results from Claude Code, Codex, Gemini CLI, and Qwen Code are useful representations of developer experience, but they must not be described as pure model comparisons.

The planned **controlled-model track** will hold the agent loop constant. Every model will receive the same tool schema, command policy, context budget, token budget, network policy, retry policy, and stopping rule. Until that track exists, the public UI and reports must use “agent” language.

## Primary metrics

- Pass@1 is primary. It reflects whether one fresh session completes the task.
- Pass@k is secondary and always states k. Retries are fresh workspaces and stop after success.
- Coverage is mandatory. Partial campaigns remain visible but are not leaderboard-eligible.
- A 95% Wilson interval accompanies binary pass rates. With nine tasks, rankings are exploratory.
- Cost and duration are aggregated per task across all attempts. Cost per pass is reported separately.
- Failure kinds distinguish agent execution, compilation, behavioral tests, policy checks, and judge infrastructure.

## Fair execution policy

Published campaigns must record the eval hash, agent-config hash, harness version, immutable model identifier, provider, CLI version, Java version, operating system, architecture, timestamps, attempt number, duration, token usage when available, reported cost, candidate snapshot hash, and the agent's final response. Results with different content identities are different benchmark cohorts.

Paid execution defaults to one attempt and requires an explicit authorization flag plus a campaign cost cap. Provider-side limits remain the final billing backstop.

## Isolation and judging

Candidate workspaces are created outside the repository so reference solutions and hidden tests are not siblings of the working directory. After an agent exits, the harness:

1. removes candidate-written tests;
2. restores the trusted Maven launcher and wrapper configuration;
3. rejects build configuration that can skip or redirect tests;
4. injects hidden tests;
5. applies trusted deterministic mechanism checks;
6. runs the behavioral tests; and
7. verifies Surefire evidence that every hidden test class executed.

This reduces accidental leakage and common test-skipping paths. It is not full OS isolation. Published campaigns should additionally run tools in containers with only the candidate workspace mounted, pre-warmed dependencies, an explicit network policy, and no access to the benchmark repository.

## Open-book and closed-book tracks

Network access changes the construct being measured:

- **Closed-book** measures learned Spring knowledge and reasoning. Workspace commands have no web access.
- **Open-book** measures realistic agent performance with identical documentation access for every model.

Results from these tracks must never be mixed.

## Holdouts and contamination

Public prompts and solutions eventually become searchable and may enter training data. The production leaderboard should use a private, rotating holdout catalog. Only the starter project and prompt are exposed during execution. Retired holdouts can later be published for audit and community learning.

## Catalog balance

The overall score should not be considered stable until the catalog has enough tasks to balance Spring portfolio, task type, difficulty, and skill dimension. Target dimensions include upgrade/API recall, diagnosis, data access, security, testing, observability, messaging, architecture, and maintainability. Boot 4.0 and Boot 4.1 should be separate versioned cohorts rather than silently replacing one another.

## Host context isolation

A benchmark run is invalid if the agent can see instructions or knowledge the host machine happens to carry: a global CLAUDE.md, user-installed skills, MCP servers, or global agent context files. On a maintainer's machine these can amount to answer keys for the very tasks being measured.

Controls, per layer:

- **Workspace**: candidate workspaces are created outside the repository, and the harness strips agent context files (CLAUDE.md, AGENTS.md, GEMINI.md, QWEN.md, .claude/, .mcp.json, Cursor and Copilot instruction files) from every fresh copy before the agent starts.
- **Claude Code**: the harness sets CLAUDE_CONFIG_DIR to an empty directory for every run, so host CLAUDE.md, skills, plugins, and MCP servers cannot load regardless of CLI defaults. This was adopted after the first real run demonstrated skill leakage under the default configuration. Subscription login does not carry into the sterile config, so Claude-family runs require ANTHROPIC_API_KEY; the upside is exact recorded cost per attempt.
- **Codex, Gemini CLI, Qwen Code**: these CLIs read global context files the harness cannot disable per invocation (~/.codex/AGENTS.md and config.toml, ~/.gemini/GEMINI.md, ~/.qwen/QWEN.md). `doctor` warns when they exist on the host. Published campaigns should run these CLIs in a container or a clean account so the warning list is empty.

Residual risk: prompt-level bans (for example the generator ban in the initializr-parity eval) rely on agent compliance and are documented as limitations rather than enforced guarantees.
