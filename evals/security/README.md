# Spring Security evals

Evals for [Spring Security](https://spring.io/projects/spring-security): authentication, authorization, and protection for Spring applications.

| Eval | Type | Difficulty | What it measures, and how models fail it |
|---|---|---|---|
| [000-lockdown](000-lockdown) | build | medium | Securing a catalog API with a stateless SecurityFilterChain: public reads, authenticated writes, an ADMIN-only area, and HTTP Basic, verified by an HTTP status matrix and a no-session-cookie check. Models trained on older content reach for the removed WebSecurityConfigurerAdapter or get matcher ordering and 401-versus-403 wrong. |
| [001-method-security](001-method-security) | fix | medium | Closing an authorization bypass where a bulk endpoint reaches admin-only service methods around the URL rules, fixed with @EnableMethodSecurity and @PreAuthorize on the operations themselves. Models fail it by patching the bulk endpoint, which passes the web tests but leaves the service open to the direct call a hidden test makes. |

Have an idea for one? Open a [benchmark proposal](../../../../issues/new?template=benchmark-proposal.yml) or see [CONTRIBUTING.md](../../CONTRIBUTING.md) to build it.
