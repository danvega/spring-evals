What this run tested: all 12 agents against boot/000-initializr-parity, one attempt each, $8 cap. Recorded spend was $3.99 (only the Claude adapter reports cost). The run produced 4 real verdicts and 8 infrastructure failures. One of the 8 was mislabeled as a verdict.

**Real verdicts (the model saw the task and the judge measured the work):**

- claude-fable-5: PASSED. Verified 4.1.0 as latest GA against Maven Central, used Boot 4 starter names, $1.43.
- claude-opus-5: PASSED. Most thorough run (288s, $1.60). Probed Maven Central for 4.1.1/4.1.2/4.0.8 to confirm 4.1.0 is newest, added test-slice starters, wrote an extra @WebMvcTest.
- claude-sonnet-5: PASSED. Best cost-to-quality of the passers ($0.83, 155s).
- claude-haiku-4-5: FAILED, and it is a genuine model failure. It built Spring Boot 4.0.0 with the old `spring-boot-starter-web` naming. The mechanism check caught the missing `spring-boot-starter-webmvc`. This is exactly the Boot 4 knowledge gap the eval exists to measure. Haiku's own summary claims success, which shows why hidden checks matter.

**Infrastructure failures (no verdict, excluded from scoring):**

- codex (sol, terra, luna): the output-parsing NPE returned, 0s duration each, so the CLI died before calling the model. Nothing spent. Prime suspect is the awk edit that stripped MCP servers from `~/.codex/config.toml` before the run. Fix: restore the backup (`mv ~/.codex/config.toml.bak ~/.codex/config.toml`), verify with `codex exec` manually, and consider a sterile CODEX_HOME in the harness so host config can never break runs again.
- gemini-2-5-flash and flash-lite: Google retired these models for new API users (404 "no longer available to new users"). The agent configs need current model IDs. Also, the CLI warned that GOOGLE_API_KEY takes precedence over GEMINI_API_KEY; decide which key should bill these runs and unset the other.
- gemini-2-5-pro: the most painful loss of the run. The model worked for 75s and built a correct project. Replaying the exact judge command on a copy of its workspace gives BUILD SUCCESS with all 3 hidden tests passing and parent 4.1.0. The CLI then exited 1, so the SDK discarded the result and no verdict was recorded. The pass was real but unmeasured. Do not hand-edit results; fix the adapter path or retry the attempt once the CLI exit cause is found.
- grok-4-5: the qwen-code CLI (0.21.5) failed to initialize its session against the x.ai endpoint (protocol NPE before any model call). Nothing spent.
- kimi-k3: recorded as a 0% verdict but it is not one. Moonshot rejected the model ID; the Claude CLI exited cleanly with the text "There's an issue with the selected model (kimi-k3)", so the harness judged the untouched workspace and logged a policy_failure in 1s with $0.00. The model never saw the task. Treat this row as an infrastructure failure when reading the scoreboard.

**Harness gap found by this run:** an agent CLI that fails but exits 0 gets scored as a real failure. The kimi row is the proof. A future hashed-harness batch should detect "workspace unchanged plus error-shaped response" and classify it agent_error instead of letting it reach the judge.

**Correction, added after the run:** while chasing the kimi failure we proved (by bytecode inspection) that the agent SDK silently drops the per-agent environment settings, so the sterile CLAUDE_CONFIG_DIR never reached the Claude CLI in this run. The empty sterile directories on disk confirm it. That means the four Claude attempts above ran with the host's own Claude config available, including Dan's Spring skills, and likely billed the Max subscription rather than the bench API key. Treat the three passes and the haiku failure as unverified until re-run under harness 0.3, which enforces the environment at process level and self-tests the mechanism before spending. The infrastructure failure analysis is unaffected.

**What to fix before the next run, in order:** restore the codex config backup and verify the CLI manually; update the two Gemini flash model IDs and settle the GOOGLE_API_KEY vs GEMINI_API_KEY question; find the right Moonshot model ID for Kimi and test it with a direct CLI call before spending; investigate the gemini CLI exit-1 and the qwen-code session failure against x.ai.
