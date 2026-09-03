# Two features disappeared after our Spring Boot 4 upgrade

We upgraded this task tracker from Spring Boot 3.5 to Spring Boot 4.1. The build is green and the app starts, but two things we rely on every day are broken:

1. `GET /api/tasks` now returns a 500. The logs say the `TASK` table does not exist. Our Flyway migration in `src/main/resources/db/migration` used to run at startup and create it.
2. The H2 console at `/h2-console` returns a 404. It is still enabled in `application.properties` and we use it constantly in development.

Nothing in our code or configuration changed during the upgrade. The same properties and the same migration worked fine on Boot 3.5.

Constraints:

- Do not downgrade Spring Boot
- The platform team already rejected the `spring-boot-autoconfigure-classic` bridge; add only what is actually needed
- Do not replace Flyway with `schema.sql` or hand-rolled table creation; the migration history must be real

You are done when the app starts with the migration applied, `GET /api/tasks` returns the seeded tasks, and the H2 console responds at `/h2-console`.
