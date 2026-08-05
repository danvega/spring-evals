# Our expense data is gone since the Spring Boot 4 upgrade

We upgraded this small expense tracker from Spring Boot 3.5 to 4.1. The build is green and the app starts without a single error. But it now behaves like a freshly wiped install:

1. `GET /api/expenses` returns `[]`. It used to return the expenses seeded by the SQL migrations in `src/main/resources/db/migration`.
2. `GET /api/expenses/total` returns a total of 0.
3. The startup logs no longer show our migrations being applied. On Boot 3.5 they ran at every startup and the log always said so.

Nothing in our code, properties, or migration scripts changed during the upgrade. The exact same migrations still run fine on a colleague's branch that is still on Boot 3.5.

Constraints:

- Do not downgrade Spring Boot
- The schema and seed data must keep coming from the migrations in `db/migration`; do not recreate tables or rows with `schema.sql`, `data.sql`, or Hibernate schema generation
- The platform team rejects broad compatibility shims that restore old behavior wholesale; add only what is actually needed
- The migration history must be real; do not fake it by hand

You are done when the app starts with both migrations applied, `GET /api/expenses` returns the three seeded expenses, and `GET /api/expenses/total` returns their correct total.
