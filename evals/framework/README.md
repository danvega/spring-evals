# Spring Framework evals

Evals for [Spring Framework](https://spring.io/projects/spring-framework): core dependency injection, transaction management, Spring MVC, data access, and messaging.

| Eval | Type | Difficulty | What it tests |
|---|---|---|---|
| [000-resilience-annotations](000-resilience-annotations) | build | medium | Core @Retryable and @ConcurrencyLimit instead of adding Spring Retry or Resilience4j |
| [001-api-versioning](001-api-versioning) | build | medium | Framework 7 native API versioning: two shapes, one path, header-selected |
| [002-problem-details](002-problem-details) | build | easy | RFC 9457 problem details with ProblemDetail and a controller advice |
| [003-transactional-self-invocation](003-transactional-self-invocation) | fix | hard | The @Transactional self-invocation proxy trap, diagnosed from a money-goes-missing symptom |

Have an idea for one? Open a [benchmark proposal](../../../../issues/new?template=benchmark-proposal.yml) or see [CONTRIBUTING.md](../../CONTRIBUTING.md) to build it.
