# Spring Data evals

Evals for [Spring Data](https://spring.io/projects/spring-data): repositories and consistent data access across relational and non-relational stores.

| Eval | Type | Difficulty | What it measures, and how models fail it |
|---|---|---|---|
| [000-n-plus-one](000-n-plus-one) | fix | hard | Recognizing an N+1 query pattern from a DBA's query log and fixing it with a fetch join or entity graph. Hidden tests count prepared statements per request through Hibernate statistics, so a fix that changes the response or leaves the lazy loads in place fails. |
| [001-repository-aot](001-repository-aot) | fix | hard | Moving Spring Data query derivation from runtime to build time, and fixing the broken finder the build-time processor exposes. Models fail it by assuming the optimization is automatic, by binding it to a build phase the test run never reaches, or by missing that the processor skipped the broken method and fell back to reflection. |

Have an idea for one? Open a [benchmark proposal](../../../../issues/new?template=benchmark-proposal.yml) or see [CONTRIBUTING.md](../../CONTRIBUTING.md) to build it.
