# Spring Data evals

Evals for [Spring Data](https://spring.io/projects/spring-data): repositories and consistent data access across relational and non-relational stores.

| Eval | Type | Difficulty | What it measures, and how models fail it |
|---|---|---|---|
| [000-n-plus-one](000-n-plus-one) | fix | hard | Recognizing an N+1 query pattern from a DBA's query log and fixing it with a fetch join or entity graph. Hidden tests count prepared statements per request through Hibernate statistics, so a fix that changes the response or leaves the lazy loads in place fails. |
| [001-repository-aot](001-repository-aot) | fix | hard | Moving Spring Data query derivation to build time with the process-aot goal, bound before the tests, and fixing the broken finder the processor exposes. Models fail it by assuming AOT is automatic, leaving process-aot on a phase that `mvnw test` never reaches, or missing that the processor skipped the broken method. |

Have an idea for one? Open a [benchmark proposal](../../../../issues/new?template=benchmark-proposal.yml) or see [CONTRIBUTING.md](../../CONTRIBUTING.md) to build it.
