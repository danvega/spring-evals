# Spring Boot evals

Evals for [Spring Boot](https://spring.io/projects/spring-boot): auto-configuration, starters, upgrades, and the opinionated application model.

| Eval | Type | Difficulty | What it measures, and how models fail it |
|---|---|---|---|
| [000-initializr-parity](000-initializr-parity) | build | medium | Generating a new Boot 4 project from an empty repository, judged against what start.spring.io produces today. Models fail it with stale tells: a Boot 2 or 3 parent, the old spring-boot-starter-web name, or an ancient Java version. |
| [001-modular-autoconfig](001-modular-autoconfig) | fix | medium | Diagnosing Flyway migrations and an H2 console that silently vanished after Boot 4 split its auto-configuration into modules. Models fail it by reaching for Boot 3 fixes or the autoconfigure-classic escape hatch instead of the Flyway starter and the H2 console module. |
| [002-restclient-migration](002-restclient-migration) | fix | easy | Migrating RestTemplate code that no longer compiles on Boot 4 to the auto-configured RestClient with the restclient starter. Models fail it by hand-constructing a client instead of injecting the auto-configured builder. A model that cannot pass this baseline has no business attempting the harder ones. |
| [003-jackson3-migration](003-jackson3-migration) | fix | medium | Migrating Jackson 2 code to the tools.jackson stack after a Boot 4 upgrade while keeping snake_case names and ISO dates. Models fail it by building their own mapper instead of using Spring's auto-configured JsonMapper, or by breaking the JSON contract along the way. |
| [004-flyway-module](004-flyway-module) | fix | medium | Diagnosing Flyway migrations that silently stopped running on Boot 4, where Hibernate hides the failure by creating empty tables. Models fail it by reaching for schema.sql, ddl-auto, or the classic bridge instead of the Boot 4 Flyway starter. |
| [005-h2-console](005-h2-console) | fix | easy | Restoring the H2 console after Boot 4 moved it into the spring-boot-h2console module. Models fail it by adjusting properties that do nothing without the module, when the fix is one missing dependency. |

Have an idea for one? Open a [benchmark proposal](../../../../issues/new?template=benchmark-proposal.yml) or see [CONTRIBUTING.md](../../CONTRIBUTING.md) to build it.
