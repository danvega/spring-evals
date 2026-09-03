# Spring Framework evals

Evals for [Spring Framework](https://spring.io/projects/spring-framework): core dependency injection, transaction management, Spring MVC, data access, and messaging.

| Eval | Type | Difficulty | What it measures, and how models fail it |
|---|---|---|---|
| [000-resilience-annotations](000-resilience-annotations) | build | medium | Adding retries and a concurrency limit with Framework 7's built-in resilience support, verified over HTTP against a flaky gateway. Models trained on older content reach for third-party retry libraries, which the prompt forbids. |
| [001-api-versioning](001-api-versioning) | build | medium | Serving two response shapes from one path with Framework 7's built-in API versioning, selected by request header with a default version. Models trained on older content hand-roll separate version paths or parse the version themselves, which the prompt forbids. |
| [002-problem-details](002-problem-details) | build | easy | Turning a default 500 into an RFC 9457 problem response for a missing resource. A floor check that a model knows modern Spring MVC error handling. |
| [003-transactional-self-invocation](003-transactional-self-invocation) | fix | hard | Fixing a partial-write bug in a service whose transactional boundary never takes effect. The symptom reads like a database problem, and models fail it by treating it as one instead of correcting where the boundary sits. |
| [004-jms-client](004-jms-client) | fix | medium | Migrating queue messaging to Framework 7's fluent client, with per-send quality of service and a request-reply call, against an embedded broker. Models fail it by repairing the symptom on the old template instead of migrating the messaging layer. |

Have an idea for one? Open a [benchmark proposal](../../../../issues/new?template=benchmark-proposal.yml) or see [CONTRIBUTING.md](../../CONTRIBUTING.md) to build it.
