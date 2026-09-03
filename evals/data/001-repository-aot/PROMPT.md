# A broken query shipped to production and the build never noticed

FieldNotes is a small CRM on Spring Boot 4.1: customers, invoices, prospects, embedded H2.

Last month marketing used the prospect lookup for the first time. `GET /api/prospects/by-domain?domain=initech.example` returned 500 in production. The finder behind it references an entity property that does not exist. It sat in the codebase for months and every build was green.

We understand why now. Spring Data derives these queries at runtime, and we run with lazy repository bootstrap (see `application.properties`) to claw back startup time. So a broken finder costs nothing until the first request hits it. Startup is also still slower than we want because every query is parsed from the method name at runtime.

Production was hot-fixed by rolling the deploy back. The bug is still in this repo.

What we want:

1. Query derivation should happen during the build, not at runtime. When the build runs, the query code for every repository should already be generated and validated, so a bad finder shows up in the build output on the CI machine instead of in production.
2. Our CI quality gate is `./mvnw clean test`. The generated query code has to exist before the test phase starts, so tests run against a build that already did the derivation work.
3. Find the broken finder that caused the incident and fix it. The lookup must return only prospects whose email ends with the requested domain.

Constraints:

- Do not downgrade Spring Boot or Spring Data.
- Keep the finders as derived query methods. Team rule for this service: no hand-written JPQL or SQL, and no `@Query`.
- Do not remove the prospect lookup. Marketing depends on it now. Endpoint paths and parameters must not change.
- Do not disable or weaken the tests.

You are done when `./mvnw clean test` is green, the repository query code is generated during the build before tests run, and the by-domain lookup returns exactly the prospects whose email ends with the requested domain.
