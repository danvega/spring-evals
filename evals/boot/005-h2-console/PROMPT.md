# The H2 console is gone since our Spring Boot 4 upgrade

We upgraded this inventory service from Spring Boot 3.5 to Spring Boot 4. The build is green, the app starts, and the product endpoints under `/api/products` work fine. But the H2 console at `/h2-console` now returns a 404.

`spring.h2.console.enabled=true` is still set in `application.properties`. It has been there for years and nothing in our code or configuration changed during the upgrade. We use the console daily for local debugging, so we need the real thing back, not a stand-in.

Constraints:

- Do not downgrade Spring Boot
- The platform team already rejected the `spring-boot-autoconfigure-classic` bridge; add only what is actually needed
- Do not hand-roll a replacement: no custom servlets, servlet registrations, or controllers pretending to be the console

You are done when the app starts, `GET /api/products` still returns the seeded catalog, and the H2 console responds at `/h2-console`.
