# Fix the build after our Spring Boot 4 upgrade

We just upgraded this order service from Spring Boot 3.5 to Spring Boot 4.0. The `pom.xml` was already updated by the platform team and they do not want it touched again.

The project no longer compiles. Run `./mvnw clean compile` to see the errors.

Our API has a published JSON contract that clients depend on. It must not change:

- Field names use snake_case: `customer_name`, `order_date`, `total_amount`
- Dates are ISO strings like `"2026-07-15"`, never numeric timestamps
- `POST /api/orders` accepts a body in that same snake_case format
- `GET /api/orders/{id}/export` returns the exact same JSON representation the API uses

Constraints:

- Do not add, remove, downgrade, or pin any dependencies in `pom.xml`
- Keep all four endpoints working: list, get by id, create, export

You are done when `./mvnw clean compile` passes and the running app still honors the contract above.
