# Spring Boot evals

Evals for [Spring Boot](https://spring.io/projects/spring-boot): auto-configuration, starters, upgrades, and the opinionated application model.

| Eval | Type | Difficulty | What it measures, and how models fail it |
|---|---|---|---|
| [000-initializr-parity](000-initializr-parity) | build | medium | Generating a new Boot 4 project from an empty repository, judged against what start.spring.io produces today. Models fail it with stale tells: an out-of-date parent version, pre-Boot-4 starter names, or an old Java release. |
| [001-modular-autoconfig](001-modular-autoconfig) | fix | medium | Diagnosing two features that silently vanished after Boot 4 split its auto-configuration into modules. The prompt rules out the compatibility bridge, so models fail it by reaching for Boot 3 era fixes instead of finding the modules that now carry each feature. |
| [002-restclient-migration](002-restclient-migration) | fix | easy | Migrating HTTP client code that no longer compiles on Boot 4 to the current client stack. Models fail it by hand-constructing a client instead of using the auto-configured builder, or by missing the starter the classpath now needs. A model that cannot pass this baseline has no business attempting the harder ones. |
| [003-jackson3-migration](003-jackson3-migration) | fix | medium | Migrating Jackson 2 code to the Jackson 3 stack after a Boot 4 upgrade while keeping snake_case names and ISO dates. Models fail it by building their own mapper instead of configuring the auto-configured one, or by breaking the JSON contract along the way. |
| [004-flyway-module](004-flyway-module) | fix | medium | Diagnosing Flyway migrations that silently stopped running after a Boot 4 upgrade, where the ORM hides the failure by creating empty tables. The only signal pointing at the cause is an absence in the logs, so models fail it by treating the symptom and seeding the schema another way instead of restoring the migration mechanism itself. |
| [005-h2-console](005-h2-console) | fix | easy | Restoring the H2 console after Boot 4 moved it out of the monolithic auto-configuration. Models fail it by adjusting properties that do nothing on their own, when the fix is the one dependency that now carries the feature. |

Have an idea for one? Open a [benchmark proposal](../../../../issues/new?template=benchmark-proposal.yml) or see [CONTRIBUTING.md](../../CONTRIBUTING.md) to build it.
