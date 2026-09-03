# Archived results, everything before harness 0.6.0

These runs are kept as a record and are deliberately out of the live results. Nothing here is a current measurement, and none of it should be quoted as one.

## Why they were cleared

Harness 0.6.0 reworked how a verdict is reached, not just how it is reported. Every run in here predates that:

- **The judge short-circuited.** Idiom checks ran before the hidden tests, so a sample that missed a check was recorded without its code ever being tested. Fifteen records here are that shape. They say `policy_failure` where the honest label is that the tests never ran.
- **At least one check was wrong.** `boot/003-jackson3-migration` required a class name its prompt never asked for, so a correct solution using Spring's auto-configured mapper was failed. That is confirmed, and it means the `lazy-cache-39` boot/003 column measured the check rather than the model.
- **Every eval changed.** The whole catalog was audited afterwards for exactly this class of defect, and moved to Spring Boot 4.1.1 and Java 26. Each eval's content hash is different now, so these records describe tasks that no longer exist in that form.
- **The oldest results are contaminated.** Before 0.3.0, agent sessions could see the host machine's Claude configuration, including installed Spring skills. Those are answer keys for these tasks.

## What is worth reading

The `.notes.md` files. They are hand-written findings, not generated, and they carry the reasoning that the raw records cannot: which failures were the model and which were the harness, what each run proved, and what it cost. `docs/VERSIONS.md` cites several of them as evidence for its per-version trust notes.

The `.md` files are the generated run logs, kept so those citations resolve.

`results-pre-0.6.0.json` holds the 62 raw records across four harness versions.

## If you want to compare

You cannot, directly. A leaderboard cohort is keyed on the full harness version, and these sit under four different ones, none of them current. Re-running under 0.6.0 is the only way to get a comparable number.
