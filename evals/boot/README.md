# Spring Boot evals

Evals for [Spring Boot](https://spring.io/projects/spring-boot): auto-configuration, starters, upgrades, and the opinionated application model.

| Eval | Type | Difficulty | What it tests |
|---|---|---|---|
| [000-initializr-parity](000-initializr-parity) | build | medium | Generating a new Boot 4 project from scratch, judged against the Spring Initializr bar |
| [001-modular-autoconfig](001-modular-autoconfig) | fix | medium | Diagnosing features that silently vanished under modular auto-configuration (Flyway, H2 console) |
| [002-restclient-migration](002-restclient-migration) | fix | easy | Migrating RestTemplate code to the auto-configured RestClient with the right starter |
| [003-jackson3-migration](003-jackson3-migration) | fix | medium | Migrating Jackson 2 code to the Jackson 3 / `tools.jackson` stack while preserving the API's JSON contract |
| [004-flyway-module](004-flyway-module) | fix | medium | Diagnosing Flyway migrations that silently stopped running on Boot 4, masked by Hibernate recreating empty tables |
| [005-h2-console](005-h2-console) | fix | easy | Restoring the H2 console after Boot 4 moved it into a dedicated auto-configuration module |

Have an idea for one? Open a [benchmark proposal](../../../../issues/new?template=benchmark-proposal.yml) or see [CONTRIBUTING.md](../../CONTRIBUTING.md) to build it.
