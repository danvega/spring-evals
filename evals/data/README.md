# Spring Data evals

Evals for [Spring Data](https://spring.io/projects/spring-data): repositories and consistent data access across relational and non-relational stores.

| Eval | Type | Difficulty | What it measures, and how models fail it |
|---|---|---|---|
| [000-n-plus-one](000-n-plus-one) | fix | hard | Recognizing an N+1 query pattern from a DBA's query log and fixing it without changing the API response. Hidden tests measure query volume per request, so a fix that only masks the symptom fails. |
| [001-repository-aot](001-repository-aot) | fix | hard | Moving Spring Data query derivation to build time and fixing the broken finder that the build-time processor exposes. Models fail it by assuming the optimization is automatic, by wiring it to a build phase the test command never reaches, or by missing the method the processor skipped. |

Have an idea for one? Open a [benchmark proposal](../../../../issues/new?template=benchmark-proposal.yml) or see [CONTRIBUTING.md](../../CONTRIBUTING.md) to build it.
