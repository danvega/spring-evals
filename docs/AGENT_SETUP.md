# Agent setup

Every agent on the leaderboard is a (CLI, model) pair defined by a JSON file in `agents/`. This guide covers signing up for each platform, getting a credential, and verifying the result. You only need to set up the agents you plan to run.

The agent CLIs themselves live in the benchmark image at pinned versions. Nothing runs on your machine; every sample runs in a fresh container that sees only the agent config env (and, for Codex, a seeded credential file). You install a CLI on the host only where it is the tool that mints a credential.

| `provider` in the config | CLI in the image | How the harness runs it |
|---|---|---|
| `claude` | `@anthropic-ai/claude-code@2.1.259` | `claude -p <prompt> --model <model> --dangerously-skip-permissions --output-format stream-json --verbose` |
| `codex` | `@openai/codex@0.152.1` | `codex exec --skip-git-repo-check --dangerously-bypass-approvals-and-sandbox --json -m <model> <prompt>` |
| `gemini` | `@google/gemini-cli@0.58.0` | `gemini -m <model> -y --skip-trust -o stream-json -p <prompt>` |
| `qwen-code` | `@qwen-code/qwen-code@0.22.3` | `qwen -y -m <model> -o stream-json -p <prompt>` |

The pins live in `harness/docker/Dockerfile` and in each `AgentCli` class, and `doctor` prints the pin it expects for every agent. The container is the sandbox, which is why each CLI runs with its own approval prompts and sandbox switched off.

Three rules before you start:

- Keys live in your shell environment, never in this repository. Agent configs reference them as `${VAR}`, and the harness expands them into the sample's container. Add exports to `~/.zshrc` or your shell's equivalent so they survive new terminals.
- After every setup step, verify with `./spring-evals doctor --agent <name>`. It checks credentials and endpoints as the container will see them, without sending a single prompt or spending anything. A host login that the config does not reference never counts. `./spring-evals doctor --json` gives the same report to scripts, and the onboarding wizard shows it in the browser.
- Anthropic and OpenAI support both subscription sign-in and API keys, and it is easy to be on the wrong one without noticing. Doctor prints the effective billing source for both, so read that line before a run.

## Anthropic (Claude family)

Runs `claude-fable-5-1`, `claude-fable-5`, `claude-opus-5`, `claude-opus-4-8`, `claude-sonnet-5`, and `claude-haiku-4-5` through [Claude Code](https://claude.com/claude-code).

1. Install the CLI on the host only to mint a token: `npm install -g @anthropic-ai/claude-code`
2. Pick a billing source (subscription is the default the configs ship with):
   - **Subscription (recommended if you have a Claude plan)**: run `claude setup-token`, complete the browser approval, and set the printed token as `CLAUDE_BENCH_OAUTH_TOKEN` in your shell. Runs draw on your plan's usage limits instead of costing API dollars. Heavy runs can hit the plan's rate windows and slow down rather than spend more.
   - **API key (exact metered costs)**: create a key at [console.anthropic.com](https://console.anthropic.com), set it as `ANTHROPIC_BENCH_API_KEY`, and change the four Claude configs' env to `"ANTHROPIC_API_KEY": "${ANTHROPIC_BENCH_API_KEY}"`. Do not export a global `ANTHROPIC_API_KEY` unless you want interactive use billed to the API too.
3. Verify: `./spring-evals doctor --family claude`. Doctor prints which billing source is active, and warns if a config declares both, because the CLI would prefer the API key.

**Why a token or key at all:** the harness runs Claude Code inside a container with an empty config directory so your CLAUDE.md, skills, plugins, and MCP servers cannot leak into a benchmark (a real run proved they otherwise do). Interactive login does not exist inside that container, so runs need a credential declared in the agent config env: the setup-token (subscription) or an API key (metered, with exact per-sample costs). Your normal interactive Claude Code is unaffected either way.

Benchmark runs give the CLI full autonomy in the workspace, so use an account whose spend you are comfortable with, and keep the campaign cap (`--max-total-cost`) below the limits you set in the provider console.

## OpenAI (GPT-5.6 family)

Runs `codex-gpt-5-6-sol`, `codex-gpt-5-6-terra`, and `codex-gpt-5-6-luna` through the Codex CLI.

1. Install the CLI on the host only to sign in: `npm install -g @openai/codex`
2. Sign in: run `codex login` with your ChatGPT account, which writes `~/.codex/auth.json`. Or create an API key at [platform.openai.com](https://platform.openai.com/api-keys) and declare it as `"OPENAI_API_KEY": "${OPENAI_BENCH_API_KEY}"` in the codex configs' env.
3. Verify: `./spring-evals doctor --family codex`

**What reaches the container:** only `~/.codex/auth.json` is seeded, into an otherwise empty `CODEX_HOME`. Your `~/.codex/config.toml` and any global AGENTS.md never reach the container, so `preferred_auth_method` has no effect there. Doctor reads the seeded credential and reports what will bill: `billing: ChatGPT sign-in seeded from ~/.codex/auth.json (subscription covers usage)`, `billing: OpenAI API key in ~/.codex/auth.json (metered API)`, or `billing: OPENAI_API_KEY from the agent config env (metered API)` when the config declares the key. If auth.json holds both a sign-in and a key, doctor warns; keep one, or declare the key in the config env to make the choice explicit.

Doctor will also note that Codex has no non-generative way to confirm the credential is valid. Confirm billing or plan limits in the OpenAI console before a long run.

## Google (Gemini)

Runs `gemini-3-1-pro`, `gemini-3-8-flash`, `gemini-3-6-flash`, and `gemini-3-5-flash-lite` through the Gemini CLI.

1. Get a key at [Google AI Studio](https://aistudio.google.com/apikey) and set `GEMINI_API_KEY`.
2. Verify: `./spring-evals doctor --family gemini`

**API key only.** A Google account sign-in lives in `~/.gemini` on the host, and the container has no such directory, so Gemini runs always bill the API key (AI Studio free tier or metered). Nothing needs to be installed on the host.

The container receives only the config env, which declares `GEMINI_API_KEY`; a host `GOOGLE_API_KEY` never reaches it, so the CLI's "GOOGLE_API_KEY takes precedence" behavior cannot silently switch which project gets billed. Doctor warns if a config declares both. Google retires older model generations for new API projects (the 2.5 line returned 404 for new users), so if doctor is green but runs fail with `NOT_FOUND`, check the model IDs in `agents/gemini-*.json` against `https://generativelanguage.googleapis.com/v1beta/models`.

## xAI (Grok)

Runs `grok-4-6` and `grok-4-5` through the Qwen Code CLI against xAI's OpenAI-compatible endpoint.

1. Create an account and API key at [console.x.ai](https://console.x.ai), add billing, and set `XAI_API_KEY`.
2. Verify: `./spring-evals doctor --agent grok-4-6`

The OpenAI-compatible path does not report spend back to the harness, so Grok's cost column shows n/a. Check the xAI console after runs and tune `estCostPerAttemptUsd` in [agents/grok-4-6.json](../agents/grok-4-6.json) as real numbers come in.

## Moonshot (Kimi)

Runs `kimi-k3` through Claude Code against Moonshot's Anthropic-compatible endpoint. Nothing needs to be installed on the host: the config sets `ANTHROPIC_BASE_URL` and passes the key as `ANTHROPIC_API_KEY`, and the Claude Code in the image does the rest.

1. Create an account and API key at [platform.moonshot.ai](https://platform.moonshot.ai) and set `MOONSHOT_API_KEY`.
2. Verify: `./spring-evals doctor --agent kimi-k3`

**Custom endpoints.** When a config sets `ANTHROPIC_BASE_URL` or `OPENAI_BASE_URL`, doctor skips the provider's billing check and instead confirms the endpoint has a credential in the config env. It refuses a `localhost` endpoint, because inside the container localhost is the container, and it probes `host.docker.internal` endpoints for the named model. This is the path Kimi, Grok, and every local model take.

## Local models (Ollama)

Local models run through the Qwen Code CLI in the container against an Ollama server on the host. Free to run, slow on laptop hardware, and heavy on disk, so no local configs ship by default. Adding one is a single JSON file.

1. Install [Ollama](https://ollama.com) and make sure it is running.
2. Pull a model, for example: `ollama pull qwen3-coder:30b` (roughly 20GB on disk).
3. Create the agent config. The base URL must use `host.docker.internal`, not `localhost`: inside the container, localhost is the container itself, and the harness maps `host.docker.internal` to your machine.

```json
{
  "name": "qwen3-coder-ollama",
  "provider": "qwen-code",
  "model": "qwen3-coder:30b",
  "env": {
    "OPENAI_BASE_URL": "http://host.docker.internal:11434/v1",
    "OPENAI_API_KEY": "ollama",
    "OPENAI_MODEL": "qwen3-coder:30b"
  },
  "estCostPerAttemptUsd": 0.0
}
```

4. Verify: `./spring-evals doctor --agent qwen3-coder-ollama`. Doctor probes the endpoint from the host side and confirms the model is actually available. A `localhost` base URL is reported as blocked, with the fix.

**Docker Model Runner** works the same way: point `OPENAI_BASE_URL` at `http://host.docker.internal:12434/engines/v1` in the agent config and use `docker model pull` instead of Ollama.

## Any other OpenAI-compatible host

Together, Fireworks, DeepSeek, and similar hosts all follow the Grok pattern: one JSON file in `agents/` with the provider set to `qwen-code`, the host's base URL, a `${VAR}` reference for the key, and an honest `estCostPerAttemptUsd`. Anthropic-compatible hosts follow the Kimi pattern through the `claude` provider instead.

## Before a run that you plan to publish

Host context contamination (global agent instruction files, MCP configuration, skills) cannot reach a sample: the container has none of your home directory. What can still skew a published campaign is a credential that bills the wrong account, so read doctor's billing line for every agent first. After the run, check the run log for contamination flags; [RUNNING.md](RUNNING.md) explains what they mean. The isolation model is described in [METHODOLOGY.md](METHODOLOGY.md) under Host context isolation.
