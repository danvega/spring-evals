# Run: first-light

Started 2026-08-05T14:21:06.905637Z. 3 of 10 samples passed. Harness 0.2.0+9e4deb544cd1.

## Findings

The test: build a new Spring Boot 4 project from an empty repository, judged against what start.spring.io produces today.

**4 real verdicts, 6 infrastructure casualties, 2 not attempted.**

- **Passed: claude-fable-5, claude-opus-5, claude-sonnet-5.** Correct Boot 4.1.0 projects with current starter names and Java 25. Caveat: installed Spring skills were visible to the Claude CLI during this run (see the Opus summary referencing a skill), so these passes validate the harness, not the models. Do not publish this cohort.
- **Failed on knowledge: claude-haiku-4-5.** Built a working project using the pre-rename `spring-boot-starter-web` and Boot 4.0.0. Its own tests passed; the mechanism check caught the outdated idiom. This is the run's cleanest finding.
- **No verdict: all three Codex agents.** Adapter bug (`defaultObjectSupplier` NPE) killed each attempt in about a second, before the model saw the task.
- **No verdict: all three Gemini agents.** This Google account requires `GOOGLE_CLOUD_PROJECT` for non-interactive use; Flash waited for a browser login until the 20-minute timeout.
- **Not attempted: grok-4-5, kimi-k3.** The cost cap tripped early because actual Claude costs exceeded two per-attempt estimates (sonnet $1.17 vs $0.50 estimated).

Billing note: `ANTHROPIC_API_KEY` in the shell took precedence over the Max plan, so the $3.97 recorded is real API spend.

## claude-fable-5 · boot/000-initializr-parity · sample 1 · pass

- model: claude-fable-5 (claude, CLI 2.1.221 (Claude Code))
- duration: 129s, tokens: n/a, cost: $1.2888635000000002
- hidden tests: passed, idiom checks: passed
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-claude-fable-5-a1-3a74401a-7d26-4b2d-bc6c-4575f9041d34

Agent's closing summary:

```
I'll set up the bookshelf project. Let me first look at what's in the repository.All files are written. Now running the test suite, which will also download Maven and the dependencies on first run.Tests pass, and the context boots with the in-memory H2 database and Hibernate wired up. Now I'll start the app and verify the endpoint.The project is set up and verified end to end: `./mvnw test` passes, the app starts on Spring Boot 4.1.0 (the current GA), and `GET /api/books` returns `200` with `[]` and `Content-Type: application/json`. I wrote everything by hand, no generator involved, and left the Maven wrapper untouched.

What I created:

- `pom.xml` — replaces the placeholder. Inherits from `spring-boot-starter-parent` 4.1.0 with `java.version=25`, and depends on `spring-boot-starter-data-jpa`, `spring-boot-starter-webmvc` (the Boot 4 name for the MVC starter, replacing `spring-boot-starter-web`), `h2` at runtime scope, and `spring-boot-starter-test`. The `spring-boot-maven-plugin` is configured for packaging and `spring-boot:run`.
- `src/main/java/com/example/bookshelf/BookshelfApplication.java` — the standard `@SpringBootApplication` class.
- `src/main/java/com/example/bookshelf/BookController.java` — `@RestController` mapped to `/api/books`; `GET` returns an empty list for now.
- `src/main/resources/application.properties` — just `spring.application.name=bookshelf`, the fresh-project default. No datasource config is needed; with H2 on the classpath, Boot auto-configures the in-memory database.
- `src/test/java/com/example/bookshelf/BookshelfApplicationTests.java` — the standard `@SpringBootTest` context-loads test.
- `.gitattributes` — the standard line-ending rules for the wrapper scripts.

Verification details: the test run boots the full context against an in-memory H2 database (Hibernate ORM 7.4.1, HikariCP pool), so JPA is wired and the team can add `@Entity` classes and repositories immediately. One environment note: the machine has JDK 26 installed, and the build compiles with `--release 25` per the pom, so the bytecode targets Java 25 as requested.
```

## claude-haiku-4-5 · boot/000-initializr-parity · sample 1 · idiom_untested

- model: claude-haiku-4-5-20251001 (claude, CLI 2.1.221 (Claude Code))
- duration: 77s, tokens: n/a, cost: $0.1031695
- hidden tests: not run, idiom checks: failed (pre-0.6.0 judge stopped before the tests)
- failure kind: policy_failure
- failure reason: required modern Spring mechanism missing from pom: spring-boot-starter-webmvc
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-claude-haiku-4-5-a1-e8301ab0-3906-4bb5-b329-4a9dd5df8b03

Agent's closing summary:

```
I'll set up this Spring Boot 4 project from scratch. Let me start by checking the current state of the project.Now I'll create the complete Spring Boot 4 project structure. First, I'll update the pom.xml with proper parent, dependencies, and build configuration.Now I'll create the directory structure and the main application class.Now I'll verify the project builds and works correctly.Tests passed! Now I'll start the application and verify the endpoint works.Project setup complete! Here's what I've created:

**Project Structure:**
- Maven build with Spring Boot 4.0.0 (latest GA) and Java 25
- Group: `com.example`, Artifact: `bookshelf`, Base package: `com.example.bookshelf`
- Standard directory layout under `src/main/java`, `src/main/resources`, `src/test/java`

**Core Components:**
- `BookshelfApplication.java` - Standard Spring Boot application class with `@SpringBootApplication`
- `BookController.java` - REST controller at `/api/books` returning an empty JSON array
- `application.properties` - H2 in-memory database configured with Spring Data JPA and Hibernate

**Dependencies:**
- Spring Boot Starter Web (MVC)
- Spring Boot Starter Data JPA
- H2 Database (runtime scope)
- Spring Boot Starter Test (test scope)

**Verification:**
- `./mvnw test` passes with context loading
- `./mvnw clean verify` succeeds
- Application starts and `GET /api/books` returns `[]`

The team can now add JPA entities and repositories to start building the bookshelf service.
```

## claude-opus-5 · boot/000-initializr-parity · sample 1 · pass

- model: claude-opus-5 (claude, CLI 2.1.221 (Claude Code))
- duration: 245s, tokens: n/a, cost: $1.4072267499999997
- hidden tests: passed, idiom checks: passed
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-claude-opus-5-a1-52ae9503-86c8-4b5e-abd3-ca8d1473e0fb

Agent's closing summary:

```
I'll start by looking at what's actually in the repository.Current GA is Spring Boot 4.1.0. Let me confirm the artifacts and check the Boot 4 starter conventions.Now let me verify the exact starter artifact names exist in 4.1.0.Verified: `spring-boot-starter-webmvc` is current (`-web` is deprecated in 4.x), and the H2 console module is `spring-boot-h2console`, not a starter. Now writing the project.Tests pass. Let me verify the bytecode target is Java 25 and find the Boot 4 test-slice class locations.Java 25 bytecode confirmed (major 69). `@WebMvcTest` isn't on the classpath (Boot 4 modularization), but `RestTestClient` and `MockMvcTester` are. Let me check the test-slice module names and the correct API.`spring-boot-starter-webmvc-test` exists for the `@WebMvcTest` slice, but a fresh project ships only `spring-boot-starter-test`. I'll test the endpoint with `RestTestClient.bindToController`, which needs no extra dependency.Both tests pass. Now let me package and verify the app actually starts and serves the endpoint.App starts clean with zero warnings and serves the endpoint. Let me note one thing I verified along the way that contradicts a skill's documentation.The bookshelf project is set up and verified end to end.

## Stack

Spring Boot **4.1.0**, the current GA. I confirmed this against the Spring release API (`4.1.0` is flagged `current: true`, `GENERAL_AVAILABILITY`) rather than assuming, since 4.0.7 is also GA on the 4.0.x line. That brings Spring Framework 7.0.8, Hibernate 7.4.1, Jackson 3.1.4, and JUnit 6.0.3. Java 25 via `<java.version>25</java.version>` (the parent defaults to 17). Compiled bytecode is major version 69, so the release target is genuinely 25 even though the local JDK is 26.

## Files

| File | Purpose |
|---|---|
| `pom.xml` | Boot 4.1.0 parent, Java 25, four dependencies, `spring-boot-maven-plugin` |
| `BookshelfApplication.java` | `@SpringBootApplication` entry point |
| `BookController.java` | `GET /api/books` |
| `application.properties` | app name, H2 datasource, JPA settings |
| `BookshelfApplicationTests.java` | standard `@SpringBootTest` context-load test |
| `BookControllerTests.java` | asserts the endpoint returns `[]` |

The Maven wrapper, `.mvn/maven.config`, and `.gitignore` are untouched.

## Two Boot 4 details worth knowing

**The web starter is `spring-boot-starter-webmvc`.** `spring-boot-starter-web` still resolves in 4.1.0, but its own POM describes it as "deprecated in favor of spring-boot-starter-webmvc". A new project today uses the new name.

**`@WebMvcTest` is no longer on the `spring-boot-starter-test` classpath.** Boot 4's modularization moved that slice into `spring-boot-starter-webmvc-test`. Rather than add a dependency a fresh project doesn't ship with, the controller test uses `RestTestClient.bindToController(...)`, which comes from `spring-test` and needs nothing extra. It runs in 44ms with no Spring context.

## Verification

```
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Packaged the jar and ran it. Tomcat came up on 8080, Hikari connected to `jdbc:h2:mem:bookshelf`, and the JPA `EntityManagerFactory` initialized. Startup logged zero WARN or ERROR lines. Live request:

```
HTTP/1.1 200
Content-Type: application/json
Content-Length: 2

[]
```

## One judgment call to flag

`BookController.findAll()` returns `List<Object>` because there is no `Book` type yet, and you asked that the team add entities themselves. The signature becomes `List<Book>` the moment the entity exists. If you'd rather I scaffold a minimal `Book` entity and repository so the return type is honest from day one, say so and I'll add them.

I also noticed the `modular-auto-config` skill lists `spring-boot-starter-h2-console` as the H2 console artifact. That one 404s on Maven Central; the real module in 4.1.0 is `spring-boot-h2console`. This project doesn't need the console, so nothing here is affected, but the skill is worth correcting.
```

## claude-sonnet-5 · boot/000-initializr-parity · sample 1 · pass

- model: claude-sonnet-5 (claude, CLI 2.1.221 (Claude Code))
- duration: 240s, tokens: n/a, cost: $1.1746516999999999
- hidden tests: passed, idiom checks: passed
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-claude-sonnet-5-a1-2b3b9894-8d84-4a50-9d02-4288aa55742d

Agent's closing summary:

```
I'll start by inspecting the existing repository structure.This is a from-scratch Spring Boot 4 setup, which involves the new modular starter naming conventions. Let me check the relevant skill before writing the POM.Let me verify the current GA Spring Boot 4 version and exact starter artifact names before writing the POM.Now I'll write the POM with Spring Boot 4.1.0 parent and the correct Boot 4 modular starter names.Now let's create the source tree: main application class, controller, resources, and the standard test.Good, the import is confirmed correct. Now let's add the controller and resources.Now let's add a `.gitignore` (standard for a fresh project) and then build.Wrapper resolves fine, Java 26 runtime (which satisfies `--release 25`). Now let's run the test suite.Build succeeds, H2/JPA wires up correctly, and the test passes. Now let's start the app and verify the endpoint serves.App started on port 8080. Let's hit the endpoint.Endpoint works as expected. Now let's stop the running app and clean up build artifacts.App stopped cleanly (process list is empty). Let's clean the build directory and do a final full verification pass.The project is set up and fully verified. Summary:

- **pom.xml**: parent `spring-boot-starter-parent:4.1.0` (current GA), Java 25, group `com.example`, artifact `bookshelf`. Dependencies use Boot 4's modular starter names: `spring-boot-starter-webmvc` (not the old `-web`), `spring-boot-starter-data-jpa`, runtime `h2`, and `spring-boot-starter-test`. Build plugin is `spring-boot-maven-plugin`.
- **BookshelfApplication.java**: standard `@SpringBootApplication` / `main` class in `com.example.bookshelf`.
- **BookController.java**: `GET /api/books` returning an empty JSON array.
- **application.properties**: just `spring.application.name=bookshelf` — H2 wires up automatically via embedded-database detection, no extra config needed.
- **BookshelfApplicationTests.java**: standard `@SpringBootTest` context-load test.
- Maven wrapper left untouched.

Verified end-to-end: `./mvnw clean verify` passes (Hikari/H2/Hibernate all initialize cleanly with 0 entities), and `./mvnw spring-boot:run` starts the app and `curl http://localhost:8080/api/books` returns `[]`. The running instance was stopped after verification.
```

## codex-gpt-5-6-luna · boot/000-initializr-parity · sample 1 · agent_error

- model: gpt-5.6-luna (codex, CLI 0.1.2504161551)
- duration: 0s, tokens: n/a, cost: n/a
- failure kind: agent_error
- failure reason: NullPointerException: Cannot invoke "java.util.function.Supplier.get()" because "defaultObjectSupplier" is null
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-codex-gpt-5-6-luna-a1-8aaee2fb-fc79-4e0d-b886-d8f4060d7f76

## codex-gpt-5-6-sol · boot/000-initializr-parity · sample 1 · agent_error

- model: gpt-5.6-sol (codex, CLI 0.1.2504161551)
- duration: 0s, tokens: n/a, cost: n/a
- failure kind: agent_error
- failure reason: NullPointerException: Cannot invoke "java.util.function.Supplier.get()" because "defaultObjectSupplier" is null
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-codex-gpt-5-6-sol-a1-99851e01-b517-417f-9647-7a6ce276d094

## codex-gpt-5-6-terra · boot/000-initializr-parity · sample 1 · agent_error

- model: gpt-5.6-terra (codex, CLI 0.1.2504161551)
- duration: 0s, tokens: n/a, cost: n/a
- failure kind: agent_error
- failure reason: NullPointerException: Cannot invoke "java.util.function.Supplier.get()" because "defaultObjectSupplier" is null
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-codex-gpt-5-6-terra-a1-b9a2892d-c2a4-4ddb-947f-8cb04277decf

## gemini-2-5-flash · boot/000-initializr-parity · sample 1 · agent_error

- model: gemini-2.5-flash (gemini, CLI 0.1.13)
- duration: 1200s, tokens: n/a, cost: n/a
- failure kind: agent_error
- failure reason: NullPointerException: Cannot invoke "java.util.function.Supplier.get()" because "defaultObjectSupplier" is null
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-gemini-2-5-flash-a1-aa213024-a740-43b5-80f0-cc5feef8b1d0

## gemini-2-5-flash-lite · boot/000-initializr-parity · sample 1 · agent_error

- model: gemini-2.5-flash-lite (gemini, CLI 0.1.13)
- duration: 332s, tokens: n/a, cost: n/a
- failure kind: agent_error
- failure reason: NullPointerException: Cannot invoke "java.util.function.Supplier.get()" because "defaultObjectSupplier" is null
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-gemini-2-5-flash-lite-a1-d53f24b5-9b06-4cc8-90df-7f0b7d3eb72f

## gemini-2-5-pro · boot/000-initializr-parity · sample 1 · agent_error

- model: gemini-2.5-pro (gemini, CLI 0.1.13)
- duration: 0s, tokens: n/a, cost: n/a
- failure kind: agent_error
- failure reason: NullPointerException: Cannot invoke "java.util.function.Supplier.get()" because "defaultObjectSupplier" is null
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-gemini-2-5-pro-a1-b8a0155c-f245-4c9b-b94b-dc3c9e2aa1ae
