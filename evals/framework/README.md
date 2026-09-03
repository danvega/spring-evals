# Spring Framework evals

Evals for [Spring Framework](https://spring.io/projects/spring-framework): core dependency injection, transaction management, Spring MVC, data access, and messaging.

| Eval | Type | Difficulty | What it measures, and how models fail it |
|---|---|---|---|
| [000-resilience-annotations](000-resilience-annotations) | build | medium | Adding retries and a concurrency limit with Framework 7's core @Retryable and @ConcurrencyLimit, verified over HTTP against a flaky gateway. Models trained on older content add Spring Retry or Resilience4j, which the prompt forbids. |
| [001-api-versioning](001-api-versioning) | build | medium | Serving two response shapes from one path with Framework 7's native API versioning, header-selected with a default version. Models trained on older content hand-roll /v1 and /v2 paths or parse the header themselves, which the prompt forbids. |
| [002-problem-details](002-problem-details) | build | easy | Turning a default 500 into an RFC 9457 application/problem+json 404 with ProblemDetail and a controller advice. A floor check that a model knows modern Spring MVC error handling. |
| [003-transactional-self-invocation](003-transactional-self-invocation) | fix | hard | Fixing a partial-write bug caused by @Transactional methods called through `this`, which the proxy never intercepts. The symptom reads like a database problem, and models fail it by treating it as one instead of moving the transaction boundary to the entry point. |
| [004-jms-client](004-jms-client) | fix | medium | Migrating JmsTemplate order messaging to Framework 7's fluent JmsClient with per-send QoS and a request-reply call, against an embedded Artemis broker. Models fail it by enabling explicit QoS on the template, which repairs the symptom and passes the behavioral tests without ever migrating the messaging layer. |

Have an idea for one? Open a [benchmark proposal](../../../../issues/new?template=benchmark-proposal.yml) or see [CONTRIBUTING.md](../../CONTRIBUTING.md) to build it.
