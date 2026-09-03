Purpose of this run: prove Claude subscription billing works inside the isolation barrier, after switching the four Claude agent configs from the API key to a `claude setup-token` credential. It does, end to end. No API dollars were spent; the four attempts drew on the Max plan. The dollar figures recorded per attempt are the CLI's own cost accounting and represent plan quota at API-equivalent prices, not money billed.

Evidence the setup is right: the CLI authenticated through CLAUDE_CODE_OAUTH_TOKEN alone inside a fresh empty config dir, and the "connectors disabled because ANTHROPIC_API_KEY is set" warning appeared zero times (it shows whenever an API key is in play).

The verdicts replicate eager-boot-85 exactly, which is the first cross-run consistency signal this benchmark has produced:

- claude-fable-5: PASSED again (161s, $1.07 plan-equivalent).
- claude-opus-5: PASSED again (242s, $1.35).
- claude-haiku-4-5: FAILED again, policy_failure, the same missing `spring-boot-starter-webmvc`.
- claude-sonnet-5: FAILED again, policy_failure, same mechanism. Two isolated runs agreeing makes this look like a real capability boundary, not noise: sonnet ships Boot 4 projects with pre-Boot-4 conventions unless something intervenes.

Why only Claude appears in this run: the billing switch changed the four Claude agent configs, which rotates their result identity on purpose. This run rebuilt their leaderboard rows under the new identity. The other agents' eager-boot-85 results are untouched and still current.

Nothing to fix. This closes the subscription-billing item: Claude runs are now effectively free (plan quota), codex already bills the ChatGPT subscription, so a full-matrix run's cash cost is down to the API-billed stragglers (gemini, grok, kimi), roughly a dollar per eval across all twelve agents.
