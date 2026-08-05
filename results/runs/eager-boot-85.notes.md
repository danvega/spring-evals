The first run where every measurement control worked. All 12 agents reached their models, isolation was enforced and verified, and the fake-verdict path is closed. 7 passed, 3 failed on the merits, 2 infrastructure failures that the evidence shows would have failed on the merits too.

**Isolation verified, not assumed.** The env self-test ran before spending. The sterile Claude config dirs are populated this run (the CLI actually used them), and the "claude.ai connectors disabled" warning is gone from every Claude attempt because the CLI no longer sees the host login. These runs measured the models, not the host machine.

**The headline result: this eval cleanly separates models on Boot 4 knowledge.** Every failure failed the same way: the old `spring-boot-starter-web` artifact instead of Boot 4's `spring-boot-starter-webmvc`. The mechanism check caught all of them.

- Passed: claude-fable-5 ($0.95), claude-opus-5 ($1.69), all three GPT-5.6 tiers (sol, terra, luna), gemini-3.6-flash, grok-4.5.
- Failed on the merits: claude-haiku-4-5, claude-sonnet-5, kimi-k3. All three policy_failure, missing spring-boot-starter-webmvc.
- The passers' transcripts show them verifying against Maven Central instead of trusting training data. The failers shipped from memory. That behavioral difference, not raw capability, decided this eval.

**The contamination proof.** claude-sonnet-5 "passed" second-light when the host config (including Dan's Spring skills) was visible to it. Under real isolation it failed. This is why the pre-0.3 results were voided and why the isolation self-test now gates every run.

**kimi-k3 is fully functional now** (the env fix delivered its Moonshot routing): 265s, 31,430 tokens, $0.48, and a real fail on the same webmvc gap.

**Infrastructure notes:**

- A widespread SDK parsing bug (the defaultObjectSupplier NullPointerException) fired on all codex, gemini, and qwen-code attempts even when the CLI succeeded. Verdicts are unaffected because judging is workspace-based, but summaries, tokens, and cost are lost for those agents (their cost columns show n/a). Worth reporting upstream to the Spring AI Community SDKs.
- gemini-3-1-pro (160s) and gemini-3-5-flash-lite (38s) are recorded as agent_error because the SDK exception fired on them, but both workspaces contain finished projects using the old starter-web naming, so neither lost a would-be pass. Classified conservatively as no-verdict.
- The Claude CLI now emits a `tool_progress` message type the SDK does not recognize. Harmless log noise, also worth an upstream report.

**ContentOS was killed again despite SERVER_PORT=0.** The transcripts show why the guard is only advisory: claude-opus-5 noticed the variable, judged it environmental noise, unset it, and bound port 8080 deliberately ("I re-verified with it unset and got 8080"). Four of the seven passing agents have no transcript (the SDK parsing bug), so exact attribution of the kill is not possible. Conclusion: no environment tweak fences in an agent with full shell access. Container isolation (DockerSandbox) is now the top harness priority, and until then a benchmark run should be treated as exclusive use of the machine.

**What this run cost:** $3.72 recorded (Claude family and kimi report cost; codex ran on the ChatGPT subscription; gemini/grok did not report). Estimated $7.47 at API list prices.

**Next steps:** report the SDK parse bug and tool_progress gap upstream; container isolation; then grow eval coverage so these one-eval snapshots become a real leaderboard.
