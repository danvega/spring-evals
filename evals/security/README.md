# Spring Security evals

Evals for [Spring Security](https://spring.io/projects/spring-security): authentication, authorization, and protection for Spring applications.

| Eval | Type | Difficulty | What it measures, and how models fail it |
|---|---|---|---|
| [000-lockdown](000-lockdown) | build | medium | Securing a catalog API: public reads, authenticated writes, an admin-only area, HTTP Basic, and no server-side session. Models trained on older content reach for a configuration base class that Spring Security removed, or get matcher ordering and 401-versus-403 wrong. |
| [001-method-security](001-method-security) | fix | medium | Closing an authorization bypass where a bulk endpoint reaches admin-only operations around the URL rules. Models fail it by patching that one endpoint, which satisfies the web-layer tests while the underlying operations stay reachable. |

Have an idea for one? Open a [benchmark proposal](../../../../issues/new?template=benchmark-proposal.yml) or see [CONTRIBUTING.md](../../CONTRIBUTING.md) to build it.
