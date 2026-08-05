# Agent setup

Every agent on the leaderboard is a (CLI, model) pair defined by a JSON file in `agents/`. This guide covers signing up for each platform, getting an API key, installing the CLI, and verifying the result. You only need to set up the agents you plan to run.

Two rules before you start:

- Keys live in your shell environment, never in this repository. Agent configs reference them as `${VAR}`, and the harness resolves them at run time. Add exports to `~/.zshrc` or your shell's equivalent so they survive new terminals.
- After every setup step, verify with `./spring-evals doctor --agent <name>`. It checks CLI presence, credentials, and endpoints without sending a single prompt or spending anything.
- Anthropic and OpenAI support both subscription sign-in and API keys, and it is easy to be on the wrong one without noticing. Doctor prints the effective billing source for both, so read that line before a run.

## Anthropic (Claude family)

Runs `claude-fable-5`, `claude-opus-5`, `claude-sonnet-5`, and `claude-haiku-4-5` through [Claude Code](https://claude.com/claude-code).

1. Install the CLI: `npm install -g @anthropic-ai/claude-code`
2. Pick a billing source (subscription is the default the configs ship with):
   - **Subscription (recommended if you have a Claude plan)**: run `claude setup-token`, complete the browser approval, and set the printed token as `CLAUDE_BENCH_OAUTH_TOKEN` in your shell. Runs draw on your plan's usage limits instead of costing API dollars. Heavy runs can hit the plan's rate windows and slow down rather than spend more.
   - **API key (exact metered costs)**: create a key at [console.anthropic.com](https://console.anthropic.com), set it as `ANTHROPIC_BENCH_API_KEY`, and change the four Claude configs' env to `"ANTHROPIC_API_KEY": "${ANTHROPIC_BENCH_API_KEY}"`. Do not export a global `ANTHROPIC_API_KEY` unless you want interactive use billed to the API too.
3. Verify: `./spring-evals doctor --family claude`. Doctor prints which billing source is active.

**Why a token or key at all:** the harness runs Claude Code inside an isolated, empty config directory so your CLAUDE.md, skills, plugins, and MCP servers cannot leak into a benchmark (a real run proved they otherwise do). Interactive login does not carry into that isolated config, so runs need a credential the harness can inject: the setup-token (subscription) or an API key (metered, with exact per-attempt costs). Your normal interactive Claude Code is unaffected either way.

Benchmark runs give the CLI full autonomy in the workspace, so use an account whose spend you are comfortable with. The Claude configs carry a per-attempt `budgetUsd` cap the CLI enforces itself.

## OpenAI (GPT-5.6 family)

Runs `codex-gpt-5-6-sol`, `codex-gpt-5-6-terra`, and `codex-gpt-5-6-luna` through the Codex CLI.

1. Install the CLI: `npm install -g @openai/codex`
2. Sign in: run `codex` and authenticate with your ChatGPT account, or create an API key at [platform.openai.com](https://platform.openai.com/api-keys) and set `OPENAI_API_KEY`.
3. Verify: `./spring-evals doctor --family codex`

**Subscription or API key?** Signing in with a ChatGPT account (Plus, Pro, or Team) covers Codex usage through the subscription; an `OPENAI_API_KEY` means metered API billing instead. Doctor reads the credential type and reports it: `billing: ChatGPT sign-in (subscription covers usage)` or `billing: OpenAI API key`. If both are present, doctor warns you to set `preferred_auth_method` in `~/.codex/config.toml` so the choice is explicit.

Doctor will also note that Codex has no non-generative way to confirm the credential is valid. Confirm billing or plan limits in the OpenAI console before a long run.

## Google (Gemini)

Runs `gemini-3-1-pro`, `gemini-3-6-flash`, and `gemini-3-5-flash-lite` through the Gemini CLI.

1. Install the CLI: `npm install -g @google/gemini-cli`
2. Sign in: run `gemini` once and complete the Google login, or get a key at [Google AI Studio](https://aistudio.google.com/apikey) and set `GEMINI_API_KEY`.
3. Verify: `./spring-evals doctor --family gemini`

**Subscription or API key?** The Google sign-in draws on the free Code Assist quota for individuals, and a Google AI Pro or Ultra plan raises those limits. An API key means AI Studio billing instead (free tier or metered). Doctor reports which one is active: `billing: Google account sign-in (plan or free Code Assist quota)` or `billing: Gemini API key`. If both are present, pick one explicitly with the CLI's `/auth` command.

During benchmark attempts the harness hides any host `GOOGLE_API_KEY` from the agent and supplies `GEMINI_API_KEY` from the agent config, so the CLI's "GOOGLE_API_KEY takes precedence" behavior cannot silently switch which project gets billed. Google retires older model generations for new API projects (the 2.5 line returned 404 for new users), so if doctor is green but runs fail with `NOT_FOUND`, check the model IDs in `agents/gemini-*.json` against `https://generativelanguage.googleapis.com/v1beta/models`.

## xAI (Grok)

Runs `grok-4-5` through the Qwen Code CLI against xAI's OpenAI-compatible endpoint.

1. Install the Qwen Code CLI: `npm install -g @qwen-code/qwen-code`
2. Create an account and API key at [console.x.ai](https://console.x.ai), add billing, and set `XAI_API_KEY`.
3. Verify: `./spring-evals doctor --agent grok-4-5`

The OpenAI-compatible path does not report spend back to the harness, so Grok's cost column shows n/a. Check the xAI console after runs and tune `estCostPerAttemptUsd` in [agents/grok-4-5.json](../agents/grok-4-5.json) as real numbers come in.

## Moonshot (Kimi)

Runs `kimi-k3` through Claude Code against Moonshot's Anthropic-compatible endpoint. No extra CLI needed beyond Claude Code.

1. Create an account and API key at [platform.moonshot.ai](https://platform.moonshot.ai) and set `MOONSHOT_API_KEY`.
2. Verify: `./spring-evals doctor --agent kimi-k3`

## Local models (Ollama)

Local models run through the Qwen Code CLI against a local Ollama server. Free to run, slow on laptop hardware, and heavy on disk, so no local configs ship by default. Adding one is a single JSON file.

1. Install [Ollama](https://ollama.com) and make sure it is running.
2. Pull a model, for example: `ollama pull qwen3-coder:30b` (roughly 20GB on disk).
3. Install the Qwen Code CLI if you have not already: `npm install -g @qwen-code/qwen-code`
4. Create the agent config:

```json
{
  "name": "qwen3-coder-ollama",
  "provider": "qwen-code",
  "model": "qwen3-coder:30b",
  "env": {
    "OPENAI_BASE_URL": "http://localhost:11434/v1",
    "OPENAI_API_KEY": "ollama",
    "OPENAI_MODEL": "qwen3-coder:30b"
  },
  "estCostPerAttemptUsd": 0.0
}
```

5. Verify: `./spring-evals doctor --agent qwen3-coder-ollama`. Doctor probes the local endpoint and confirms the model is actually available.

**Docker Model Runner** works the same way: point `OPENAI_BASE_URL` at `http://localhost:12434/engines/v1` in the agent config and use `docker model pull` instead of Ollama.

## Any other OpenAI-compatible host

Together, Fireworks, DeepSeek, and similar hosts all follow the Grok pattern: one JSON file in `agents/` with the provider set to `qwen-code`, the host's base URL, a `${VAR}` reference for the key, and an honest `estCostPerAttemptUsd`. Anthropic-compatible hosts follow the Kimi pattern through the `claude` provider instead.

## Before a run that you plan to publish

Doctor also warns about host context contamination: global agent instruction files and MCP configuration that some CLIs load from your home directory. Resolve every warning before a published campaign; the details are in [METHODOLOGY.md](METHODOLOGY.md) under Host context isolation.
