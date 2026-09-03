# Fix the HTTP client after our Spring Boot 4 upgrade

We upgraded this quotes service from Spring Boot 3.5 to Spring Boot 4.1 and the build broke. Run `./mvnw clean compile` to see it: the compiler can no longer find the builder our HTTP client code was based on.

While you are in there, the team wants this done properly rather than patched:

- Use the current Spring Boot HTTP client stack with its auto-configured builder. The platform team's client customizations (timeouts, headers, observability) must keep applying to our outbound calls
- Do not construct clients by hand with `new`
- You may add Spring Boot starters to the pom if something is missing; no third-party libraries
- `GET /api/quotes` must keep returning the quotes from the partner API
- `PartnerQuotesController` is our local stand-in for the partner and must not change, and keep the base URL resolution in `QuoteService` working as it does today

You are done when the build is green and `GET /api/quotes` serves the partner quotes again.
