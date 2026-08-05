The test: build a new Spring Boot 4 project from an empty repository, judged against what start.spring.io produces today.

**4 real verdicts, 6 infrastructure casualties, 2 not attempted.**

- **Passed: claude-fable-5, claude-opus-5, claude-sonnet-5.** Correct Boot 4.1.0 projects with current starter names and Java 25. Caveat: installed Spring skills were visible to the Claude CLI during this run (see the Opus summary referencing a skill), so these passes validate the harness, not the models. Do not publish this cohort.
- **Failed on knowledge: claude-haiku-4-5.** Built a working project using the pre-rename `spring-boot-starter-web` and Boot 4.0.0. Its own tests passed; the mechanism check caught the outdated idiom. This is the run's cleanest finding.
- **No verdict: all three Codex agents.** Adapter bug (`defaultObjectSupplier` NPE) killed each attempt in about a second, before the model saw the task.
- **No verdict: all three Gemini agents.** This Google account requires `GOOGLE_CLOUD_PROJECT` for non-interactive use; Flash waited for a browser login until the 20-minute timeout.
- **Not attempted: grok-4-5, kimi-k3.** The cost cap tripped early because actual Claude costs exceeded two per-attempt estimates (sonnet $1.17 vs $0.50 estimated).

Billing note: `ANTHROPIC_API_KEY` in the shell took precedence over the Max plan, so the $3.97 recorded is real API spend.
