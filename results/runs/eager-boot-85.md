# Run: eager-boot-85

Started 2026-08-05T17:14:41.342823Z. 7 of 12 samples passed. Harness 0.3.0+11b0497585a6.

## Findings

The first run where every measurement control worked. All 12 agents reached their models, isolation was enforced and verified, and the fake-verdict path is closed. 7 passed, 3 failed on the merits, 2 infrastructure failures that the evidence shows would have failed on the merits too.

**Isolation verified, not assumed.** The env self-test ran before spending. The sterile Claude config dirs are populated this run (the CLI actually used them), and the "claude.ai connectors disabled" warning is gone from every Claude attempt because the CLI no longer sees the host login. These runs measured the models, not the host machine.

**The headline result: this eval cleanly separates models on Boot 4 knowledge.** Every failure failed the same way: the old `spring-boot-starter-web` artifact instead of Boot 4's `spring-boot-starter-webmvc`. The mechanism check caught all of them.

- Passed: claude-fable-5 ($0.95), claude-opus-5 ($1.69), all three GPT-5.6 tiers (sol, terra, luna), gemini-3.6-flash, grok-4.5.
- Failed on the merits: claude-haiku-4-5, claude-sonnet-5, kimi-k3. All three policy_failure, missing spring-boot-starter-webmvc.
- The passers' transcripts show them verifying against Maven Central instead of trusting training data. The failers shipped from memory. That behavioral difference, not raw capability, decided this eval.

**The contamination proof.** claude-sonnet-5 "passed" second-light when the host config (including Dan's Spring skills) was visible to it. Under real isolation it failed. This is why the pre-0.3 results were voided and why the isolation self-test now gates every run.

**kimi-k3 is fully functional now** (the env fix delivered its Moonshot routing): 265s, 31,430 tokens, $0.48, and a real fail on the same webmvc gap.

**Infrastructure notes:**

- A widespread SDK parsing bug (the defaultObjectSupplier NullPointerException) fired on all codex, gemini, and qwen-code attempts even when the CLI succeeded. Verdicts are unaffected because judging is workspace-based, but summaries, tokens, and cost are lost for those agents (their cost columns show n/a). Worth reporting upstream to the Spring AI Community SDKs.
- gemini-3-1-pro (160s) and gemini-3-5-flash-lite (38s) are recorded as agent_error because the SDK exception fired on them, but both workspaces contain finished projects using the old starter-web naming, so neither lost a would-be pass. Classified conservatively as no-verdict.
- The Claude CLI now emits a `tool_progress` message type the SDK does not recognize. Harmless log noise, also worth an upstream report.

**ContentOS was killed again despite SERVER_PORT=0.** The transcripts show why the guard is only advisory: claude-opus-5 noticed the variable, judged it environmental noise, unset it, and bound port 8080 deliberately ("I re-verified with it unset and got 8080"). Four of the seven passing agents have no transcript (the SDK parsing bug), so exact attribution of the kill is not possible. Conclusion: no environment tweak fences in an agent with full shell access. Container isolation (DockerSandbox) is now the top harness priority, and until then a benchmark run should be treated as exclusive use of the machine.

**What this run cost:** $3.72 recorded (Claude family and kimi report cost; codex ran on the ChatGPT subscription; gemini/grok did not report). Estimated $7.47 at API list prices.

**Next steps:** report the SDK parse bug and tool_progress gap upstream; container isolation; then grow eval coverage so these one-eval snapshots become a real leaderboard.

## claude-fable-5 · boot/000-initializr-parity · sample 1 · pass

- model: claude-fable-5 (claude, CLI 2.1.221 (Claude Code))
- duration: 194s, tokens: n/a, cost: $0.9491755
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-claude-fable-5-a1-8033081d-6ab4-44e2-b62e-2c0f6f6b1cb9

Agent's closing summary:

```
I'll set up the bookshelf project. First, let me look at what's in the repo.The Maven Central search index looks stale (says 3.5.3 from June 2025). Let me check the repository metadata directly for the real latest version.Latest GA is Spring Boot 4.1.0 (June 2026). Spring Boot 4 renamed some starters (e.g. `spring-boot-starter-web` → `spring-boot-starter-webmvc`), so let me verify the exact artifact names against the 4.1.0 BOM.Files are in place. Now running the test suite (this first run downloads dependencies, so it may take a few minutes).The app started fine but on port 64011 — something in this environment overrides the port. Let me re-run and curl the actual port from the log.The project is set up and verified end to end. `GET /api/books` returns HTTP 200 with body `[]` and `Content-Type: application/json` on a running Spring Boot 4.1.0 app, and `./mvnw test` passes.

## What I built

A hand-written project matching what a brand-new Spring Boot 4 project starts with today:

- **`pom.xml`** — parent `spring-boot-starter-parent` **4.1.0** (the current GA line; I verified against Maven Central's metadata directly since the search index was stale), `java.version` 25, group `com.example`, artifact `bookshelf`, and the `spring-boot-maven-plugin`. Dependencies: `spring-boot-starter-webmvc` (Boot 4 renamed the MVC starter — the old `spring-boot-starter-web` is now a deprecated alias, which I confirmed from its published POM), `spring-boot-starter-data-jpa`, `h2` (runtime scope), and `spring-boot-starter-test` (test scope).
- **`src/main/java/com/example/bookshelf/BookshelfApplication.java`** — standard `@SpringBootApplication` class.
- **`src/main/java/com/example/bookshelf/BookController.java`** — `@RestController` mapped to `/api/books`, returning an empty list for now.
- **`src/main/resources/application.properties`** — `spring.application.name=bookshelf`. H2 needs no config: with the driver on the classpath, Boot auto-configures an in-memory database, and the test logs show Hibernate initializing the JPA `EntityManagerFactory` against H2, so the team can add `@Entity` classes and repositories immediately.
- **`src/test/java/com/example/bookshelf/BookshelfApplicationTests.java`** — the standard `@SpringBootTest` `contextLoads` test.

The Maven wrapper, `.mvn/` config, and `.gitignore` were left untouched.

## Verification

- `./mvnw test`: **BUILD SUCCESS**, 1 test run, 0 failures.
- `./mvnw spring-boot:run`: started in ~0.9s on Boot 4.1.0; curling `/api/books` returned `200` / `[]`. One note: your shell environment exports `SERVER_PORT=0`, so the app binds a random port here (64143 in my run) instead of 8080 — that's environmental, not project config; on a normal machine it will serve on 8080.
```

## claude-haiku-4-5 · boot/000-initializr-parity · sample 1 · policy_failure

- model: claude-haiku-4-5-20251001 (claude, CLI 2.1.221 (Claude Code))
- duration: 83s, tokens: n/a, cost: $0.12250174999999999
- failure kind: policy_failure
- failure reason: required modern Spring mechanism missing from pom: spring-boot-starter-webmvc
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-claude-haiku-4-5-a1-e30ae9d8-9b94-4ea3-a3d0-9efc2cae1a79

Agent's closing summary:

```
I'll set up the Spring Boot project from scratch. Let me start by examining the current state and then build out the complete project structure.Now I'll set up the complete Spring Boot 4 project. Let me create the pom.xml with the necessary dependencies, then the application class, controller, and test.Now I'll create the application class, controller, configuration, and test:Now let me build the project to verify everything works:Perfect! Tests pass. Now let me start the application and verify the endpoint works:Excellent! The project is fully set up and working. Let me verify the complete structure:Perfect! The Spring Boot 4 project is fully set up and operational. Here's what's been created:

## Project Summary

**✓ Spring Boot 4.0.0** (current GA generation)  
**✓ Java 25** with Maven build  
**✓ Group:** `com.example` | **Artifact:** `bookshelf` | **Package:** `com.example.bookshelf`

### Components Created

1. **pom.xml** — Spring Boot parent with dependencies for:
   - Spring MVC (Web)
   - Spring Data JPA
   - H2 in-memory database
   - Standard test framework (JUnit 5, Mockito)

2. **Application Class** — `BookshelfApplication.java` with `@SpringBootApplication`

3. **REST Controller** — `BookController.java` with `GET /api/books` endpoint returning empty JSON array

4. **Database Configuration** — `application.properties` with H2 in-memory database setup and H2 console enabled

5. **Test Class** — `BookshelfApplicationTests.java` with context load test

6. **Maven Wrapper** — Preserved as-is

### Verification

- ✅ **Build**: `./mvnw clean package` succeeds
- ✅ **Tests**: `./mvnw test` passes (1 test)
- ✅ **Endpoint**: `GET /api/books` returns `[]`
- ✅ **Database**: H2 wired and ready for entities

The team can now start adding JPA entities and repository interfaces immediately. The project follows Spring Boot conventions and is ready for development.
```

## claude-opus-5 · boot/000-initializr-parity · sample 1 · pass

- model: claude-opus-5 (claude, CLI 2.1.221 (Claude Code))
- duration: 338s, tokens: n/a, cost: $1.69475
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-claude-opus-5-a1-9efdd6d7-1295-4c45-a314-99409055c2b0

Agent's closing summary:

```
I'll start by exploring the existing repository state.Confirmed the current stack: **Spring Boot 4.1.0** (latest GA, June 2026), and Boot 4's modularization means `spring-boot-starter-web` is deprecated in favor of `spring-boot-starter-webmvc`, with a companion `-test` starter per starter. Writing the project now.Build and tests pass. Now verifying the app actually starts and serves the endpoint from the packaged jar.`SERVER_PORT=0` is set in this shell by the eval harness — not a project issue. Re-running with the standard port:`spring-boot-h2console` is a separate module in Boot 4 and isn't pulled in by the JPA starter, so that property was dead config. Removing it.Let me verify the "add entities immediately" claim end to end with a throwaway entity, then remove it.The project is set up and verified end to end.

## What I built

```
pom.xml
src/main/java/com/example/bookshelf/BookshelfApplication.java
src/main/java/com/example/bookshelf/BookController.java
src/main/resources/application.properties
src/test/java/com/example/bookshelf/BookshelfApplicationTests.java
src/test/java/com/example/bookshelf/BookControllerTests.java
```

Maven wrapper, `.mvn/`, and `.gitignore` untouched. Everything hand-written — no generator, no copied project.

## Version and convention choices

I pinned versions from `maven-metadata.xml` on Maven Central rather than from memory. Latest GA is **Spring Boot 4.1.0** (June 2026), which manages Spring Framework 7.0.8, Hibernate 7.4.1, Jackson 3.1.4, JUnit 6.0.3, H2 2.4.240. Java 25 via `<java.version>25</java.version>`.

The non-obvious part is Boot 4's starter modularization, which I verified against the published POMs rather than assuming Boot 3 habits carried over:

- **`spring-boot-starter-webmvc`, not `spring-boot-starter-web`** — the latter's own POM description reads "deprecated in favor of `spring-boot-starter-webmvc`".
- **Per-starter `-test` companions** — `spring-boot-starter-test` on its own no longer carries the test slices. I confirmed by unzipping `spring-boot-test-autoconfigure-4.1.0.jar`: `@WebMvcTest` and `@DataJpaTest` are *not* in it. They now live in `spring-boot-webmvc-test` / `spring-boot-data-jpa-test`, reached via the matching test starters, and `@WebMvcTest` moved to `org.springframework.boot.webmvc.test.autoconfigure`.

## Verification

- `./mvnw clean verify` → BUILD SUCCESS, 2/2 tests pass, jar repackaged.
- App starts on 8080 and `GET /api/books` → `HTTP 200`, `application/json`, `[]`.
- **"Ready for entities" checked for real**, not assumed: I temporarily added a throwaway `@Entity` + `JpaRepository`, started the app, and confirmed Hibernate emitted `create table tmp_probe (...)` against H2, then deleted the probe files. The team can add entities with zero further config.

## Two things worth flagging

**`SERVER_PORT=0` is set in this shell's environment** (from the harness, not the project). Spring Boot reads it as `server.port=0`, so my first run bound a random port. I re-verified with it unset and got 8080. Nothing to fix in the project, but it will bite anyone running in this same shell.

**I dropped an H2 console setting I had initially added.** I wrote `spring.h2.console.enabled=true`, then found `/h2-console` returning 404 — in Boot 4 the console is a separate `spring-boot-h2console` module that the JPA starter does not pull in, so the property was dead config. Rather than leave a line that silently does nothing, I removed it. If the team wants the console, it needs that dependency added explicitly.

`application.properties` holds `spring.application.name=bookshelf` plus an explicit `jdbc:h2:mem:bookshelf` URL. The URL isn't strictly required — Boot would auto-configure a randomly-named in-memory H2 — but naming it makes the H2 wiring visible in config instead of implicit, and being an embedded URL it still gets `ddl-auto=create-drop` by default.

One judgment call to name: `BookController.findAll()` returns `List<?>` and an empty `List.of()`. I deliberately did *not* create a placeholder `Book` type, since the team is about to add a `Book` `@Entity` and a stub would collide with it. Swapping `List<?>` for `List<Book>` is a one-line change once that entity exists.

Sources: [Modularizing Spring Boot](https://spring.io/blog/2025/10/28/modularizing-spring-boot/), [@WebMvcTest (Boot 4.1.0 API)](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/webmvc/test/autoconfigure/WebMvcTest.html), [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
```

## claude-sonnet-5 · boot/000-initializr-parity · sample 1 · policy_failure

- model: claude-sonnet-5 (claude, CLI 2.1.221 (Claude Code))
- duration: 122s, tokens: n/a, cost: $0.4662454999999999
- failure kind: policy_failure
- failure reason: required modern Spring mechanism missing from pom: spring-boot-starter-webmvc
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-claude-sonnet-5-a1-1e47439e-e1a3-4b1d-8b91-44fec996a561

Agent's closing summary:

```
I'll start by inspecting what's already in the repo.Let me check the available JDK version and look up the current GA Spring Boot 4 version to make sure I use accurate, up-to-date version numbers.Now I have a clear picture: Spring Boot 4.1.0 (GA June 2026, requires Spring Framework 7.0.8+) is the current stable line, Java baseline stays at 17 with first-class Java 25 support, Jakarta EE 11 baseline. Let me build the project files.Now let's create the application class, controller, resources, and test.I referenced a nonexistent `Book` type — since no entities exist yet, I'll simplify the controller to just return an empty list without inventing a domain type.Now let's build and test. First checking mvnw is executable, then running the build.Wrapper works and downloaded Maven itself successfully. Now let's run the actual build with tests.Build succeeded, JPA/H2/Hibernate 7 wired correctly, and the context test passed. Now let's start the app and verify the actual endpoint works.App started successfully on Tomcat 11 with H2/Hikari/Hibernate 7 wired. Now let's hit the endpoint.The endpoint returns `200 OK` with body `[]` exactly as required. Let's shut down the app cleanly.The project is set up, built, started, and verified end-to-end.

**Summary:**
- `pom.xml`: Maven project using `spring-boot-starter-parent:4.1.0` (current GA Spring Boot 4 line as of Aug 2026), Java 25 target, group `com.example` / artifact `bookshelf`, with `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `h2` (runtime), and `spring-boot-starter-test`, plus the `spring-boot-maven-plugin`.
- `BookshelfApplication.java` — standard `@SpringBootApplication` main class.
- `BookController.java` — `GET /api/books` returning `[]`.
- `application.properties` — just `spring.application.name=bookshelf` (H2 wires itself in automatically once `h2` + `spring-boot-starter-data-jpa` are on the classpath — no entities needed yet).
- `BookshelfApplicationTests.java` — standard `@SpringBootTest` context-loads test.
- Kept the existing Maven wrapper untouched.

Verified: `./mvnw clean verify` builds and passes tests (Hibernate 7 / Tomcat 11 / Jakarta EE 11 stack came in automatically via the Boot 4 BOM), and running the packaged jar serves `GET /api/books` → `200 OK`, body `[]`.
```

## codex-gpt-5-6-luna · boot/000-initializr-parity · sample 1 · pass

- model: gpt-5.6-luna (codex, CLI codex-cli 0.146.0)
- duration: 223s, tokens: n/a, cost: n/a
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-codex-gpt-5-6-luna-a1-a7c77540-fdc0-494a-87bb-aa984bd1f232

## codex-gpt-5-6-sol · boot/000-initializr-parity · sample 1 · pass

- model: gpt-5.6-sol (codex, CLI codex-cli 0.146.0)
- duration: 242s, tokens: n/a, cost: n/a
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-codex-gpt-5-6-sol-a1-6290b948-f173-42b8-bc3d-1f52deba732a

## codex-gpt-5-6-terra · boot/000-initializr-parity · sample 1 · pass

- model: gpt-5.6-terra (codex, CLI codex-cli 0.146.0)
- duration: 250s, tokens: n/a, cost: n/a
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-codex-gpt-5-6-terra-a1-921c9005-78b6-4a55-9bd5-761954575e21

## gemini-3-1-pro · boot/000-initializr-parity · sample 1 · agent_error

- model: gemini-3.1-pro-preview (gemini, CLI 0.1.13)
- duration: 160s, tokens: n/a, cost: n/a
- failure kind: agent_error
- failure reason: NullPointerException: Cannot invoke "java.util.function.Supplier.get()" because "defaultObjectSupplier" is null
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-gemini-3-1-pro-a1-efd6d94e-ff5b-4e6e-926f-282d560d0d4e

## gemini-3-5-flash-lite · boot/000-initializr-parity · sample 1 · agent_error

- model: gemini-3.5-flash-lite (gemini, CLI 0.1.13)
- duration: 38s, tokens: n/a, cost: n/a
- failure kind: agent_error
- failure reason: NullPointerException: Cannot invoke "java.util.function.Supplier.get()" because "defaultObjectSupplier" is null
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-gemini-3-5-flash-lite-a1-abdefeaa-1ffe-4d03-ad82-45f9e8f57eb2

## gemini-3-6-flash · boot/000-initializr-parity · sample 1 · pass

- model: gemini-3.6-flash (gemini, CLI 0.1.13)
- duration: 175s, tokens: n/a, cost: n/a
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-gemini-3-6-flash-a1-af0b9dcf-7549-4927-88de-54c0cf6eccf0

## grok-4-5 · boot/000-initializr-parity · sample 1 · pass

- model: grok-4.5 (qwen-code, CLI 0.21.5)
- duration: 77s, tokens: n/a, cost: n/a
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-grok-4-5-a1-7954aa45-5277-4c74-92eb-5580eafdc863

## kimi-k3 · boot/000-initializr-parity · sample 1 · policy_failure

- model: kimi-k3 (claude, CLI 2.1.221 (Claude Code))
- duration: 265s, tokens: n/a, cost: $0.48291699999999993
- failure kind: policy_failure
- failure reason: required modern Spring mechanism missing from pom: spring-boot-starter-webmvc
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-kimi-k3-a1-29d504d1-928d-4533-8c3e-72b7b25a190a

Agent's closing summary:

```
Latest GA is Spring Boot 4.1.0. Let me verify the starter artifact names in Boot 4.`spring-boot-starter-web` is still the standard Spring MVC + Tomcat starter in Boot 4. Writing the project now.Tests ran — let me confirm the build result and then start the app and hit the endpoint.The app started but on a random port — something is setting `server.port=0`. Let me check `.mvn/maven.config`.The endpoint works — `[]` with HTTP 200. (The random port was caused by a `SERVER_PORT=0` env var in this shell, not the project; default is 8080.) Let me stop the app.The app is stopped (the only matches left are the shell command itself). The project is set up and verified.

## Summary

**Stack**: Spring Boot **4.1.0** (latest GA, verified against Maven Central), Spring Framework 7, Hibernate 7.4, Tomcat 11, Java 25, Maven via the existing wrapper.

**Files written:**

- `pom.xml` — parent `spring-boot-starter-parent:4.1.0`, `com.example:bookshelf:0.0.1-SNAPSHOT`, `java.version=25`, dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `com.h2database:h2` (runtime), `spring-boot-starter-test` (test), plus the `spring-boot-maven-plugin`
- `src/main/java/com/example/bookshelf/BookshelfApplication.java` — standard `@SpringBootApplication` main class
- `src/main/java/com/example/bookshelf/BookController.java` — `@RestController` with `GET /api/books` returning an empty list
- `src/main/resources/application.properties` — `spring.application.name=bookshelf` (H2 auto-configures as an in-memory datasource by convention, no extra config needed)
- `src/test/java/com/example/bookshelf/BookshelfApplicationTests.java` — `@SpringBootTest` context-loads test

The Maven wrapper was left untouched, and everything was written by hand — no generator used.

**Verified:**
- `./mvnw test` → `Tests run: 1, Failures: 0, Errors: 0` → **BUILD SUCCESS** (context boots with H2 + JPA wiring live)
- `./mvnw spring-boot:run` starts cleanly and `GET /api/books` returns `[]` with HTTP 200

One note: this shell has `SERVER_PORT=0` exported, so the app bound to a random port during verification — that's environmental, not from the project; in a normal shell it serves on 8080. The team can now add a `Book` entity and `JpaRepository` and the endpoint is ready to be backed by real persistence.
```
