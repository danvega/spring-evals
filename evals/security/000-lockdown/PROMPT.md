# Lock down the catalog API

This product catalog API currently has the Spring Security starter on the classpath but no security configuration, so the default behavior locks everything behind a generated password. We need a real security setup before launch.

The contract our clients and the mobile team agreed on:

- Reading the catalog is public: `GET /api/products` and `GET /api/products/{id}` must work without credentials
- Creating products requires an authenticated user: `POST /api/products`
- Everything under `/api/admin/` requires the `ADMIN` role
- Authentication is HTTP Basic. This is a stateless API for machine clients: no sessions, no session cookies on responses
- Set up two in-memory users for now: `user` with password `user-pass` (role `USER`), and `admin` with password `admin-pass` (roles `USER` and `ADMIN`)

Constraints:

- Do not add or remove dependencies
- Do not change the controllers or the response shapes
- Unauthenticated requests to protected endpoints must get a 401, authenticated users without the right role must get a 403

You are done when the contract above holds exactly.
