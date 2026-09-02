The first paid run under the frozen 0.5.0 harness, and the first ever through containers: Claude family, all six boot evals, one attempt each, 24 attempts. Every record carries the `docker:` prefix, proving the whole run executed in per-attempt containers.

**Infrastructure: perfect.** Zero agent errors, zero judge errors, zero no-verdict rows. The container agent path, the container judge, the selection config, and subscription billing all worked on their first live outing. The docker agent path is no longer unproven; this run is its proof.

**The first leaderboard-eligible rows in project history.** Full 6/6 coverage means no "partial" tags:

- claude-fable-5: 6/6, 100% Pass@1, fastest and cheapest of the top tier (93s, $0.89 plan-equivalent per task).
- claude-opus-5: 6/6, 100%, twice fable's tokens and time for the same verdicts.
- claude-sonnet-5: 5/6, 83%. Its one miss is Jackson 3: it migrated the code without adopting `tools.jackson.databind.json.JsonMapper`, the exact new-API knowledge the eval measures.
- claude-haiku-4-5: 0/6. Every failure is a policy_failure, and they tell one coherent story: haiku ships pre-Boot-4 idioms. Missing `spring-boot-starter-restclient`, missing the webmvc starter, hand-rolled `Flyway.configure` instead of the Boot 4 module, and so on. It often produces code that runs; the mechanism checks catch that it is last year's Spring.

**What this says about the benchmark itself:** the six evals now separate a model family into four distinct tiers (100 / 100 / 83 / 0) with tightening confidence intervals (fable and opus at 61 to 100 percent, haiku at 0 to 39). One suite and one family is still a narrow lens, but this is the first result set with no asterisks: honest isolation, identical toolchains, hidden judges, and every verdict a real one.

**Cost:** $17.50 plan-equivalent accounting, $0.00 billed; the whole run drew on the Max subscription. Wall clock about two hours, serial within the single provider lane. The parallel scheduler remains free-verified only; it needs a multi-provider run to earn its proof.

**Nothing to fix.** First run in project history where the findings contain no infrastructure section and no follow-up list.

**Correction, added 2026-09-02.** The sonnet miss on boot/003 was a judge defect, not a knowledge gap. Sonnet's solution used a `JsonMapperBuilderCustomizer` and injected `ObjectMapper` by type, which resolves to Boot 4's auto-configured JsonMapper. A reconstruction of that shape passes all five hidden tests. The required pattern demanded the literal `JsonMapper` class name, which the prompt never asked for, and the judge stopped at the pattern before running the tests. The boot/003 checks have since been rewritten and the hidden tests strengthened, which rotates that eval's hash and drops its four rows from the cohort. Haiku's boot/003 row was scored by the same pattern and should not be cited either. Read this run as five evals, not six, until boot/003 is re-run under harness 0.6.0.
