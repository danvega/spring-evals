# Run: sturdy-mono-00

Started 2026-08-05T20:02:31.866067Z. 2 of 4 samples passed. Harness 0.3.0+11b0497585a6.

## Findings

Purpose of this run: prove Claude subscription billing works inside the isolation barrier, after switching the four Claude agent configs from the API key to a `claude setup-token` credential. It does, end to end. No API dollars were spent; the four attempts drew on the Max plan. The dollar figures recorded per attempt are the CLI's own cost accounting and represent plan quota at API-equivalent prices, not money billed.

Evidence the setup is right: the CLI authenticated through CLAUDE_CODE_OAUTH_TOKEN alone inside a fresh empty config dir, and the "connectors disabled because ANTHROPIC_API_KEY is set" warning appeared zero times (it shows whenever an API key is in play).

The verdicts replicate eager-boot-85 exactly, which is the first cross-run consistency signal this benchmark has produced:

- claude-fable-5: PASSED again (161s, $1.07 plan-equivalent).
- claude-opus-5: PASSED again (242s, $1.35).
- claude-haiku-4-5: FAILED again, policy_failure, the same missing `spring-boot-starter-webmvc`.
- claude-sonnet-5: FAILED again, policy_failure, same mechanism. Two isolated runs agreeing makes this look like a real capability boundary, not noise: sonnet ships Boot 4 projects with pre-Boot-4 conventions unless something intervenes.

Why only Claude appears in this run: the billing switch changed the four Claude agent configs, which rotates their result identity on purpose. This run rebuilt their leaderboard rows under the new identity. The other agents' eager-boot-85 results are untouched and still current.

Nothing to fix. This closes the subscription-billing item: Claude runs are now effectively free (plan quota), codex already bills the ChatGPT subscription, so a full-matrix run's cash cost is down to the API-billed stragglers (gemini, grok, kimi), roughly a dollar per eval across all twelve agents.

## claude-fable-5 · boot/000-initializr-parity · sample 1 · pass

- model: claude-fable-5 (claude, CLI 2.1.221 (Claude Code))
- duration: 161s, tokens: n/a, cost: $1.074725
- hidden tests: passed, idiom checks: passed
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-claude-fable-5-a1-4a689aa5-aa1a-4d79-9f7d-2d655a2c79b0

Agent's closing summary:

```
I'll set up the bookshelf Spring Boot project. Let me first look at what's in the repository.Now running the test suite, which will also download the wrapper's Maven distribution and dependencies on first run.Tests pass and JPA/H2 wired up correctly. Now verifying the app starts and serves the endpoint.The project is set up and verified working. Here's a summary:

## What was built

A fresh Spring Boot **4.1.0** project (the current GA release on Maven Central) on **Java 25**, written by hand with the layout and conventions a brand-new project starts with today:

- **`pom.xml`** — parent `spring-boot-starter-parent:4.1.0`, `<java.version>25</java.version>`, and the standard dependency set: `spring-boot-starter-data-jpa`, `spring-boot-starter-webmvc` (the Boot 4 name for the MVC starter, replacing the old `spring-boot-starter-web`), `com.h2database:h2` at runtime scope, `spring-boot-starter-test` at test scope, plus the `spring-boot-maven-plugin`.
- **`src/main/java/com/example/bookshelf/BookshelfApplication.java`** — the standard `@SpringBootApplication` class.
- **`src/main/java/com/example/bookshelf/BookController.java`** — `@RestController` mapping `GET /api/books` to an empty list.
- **`src/main/resources/application.properties`** — `spring.application.name=bookshelf`. Nothing more is needed for the database: Boot auto-configures the in-memory H2 datasource and Hibernate as soon as H2 and JPA are on the classpath.
- **`src/test/java/com/example/bookshelf/BookshelfApplicationTests.java`** — the standard `@SpringBootTest` context-loads test.

The Maven wrapper, `.mvn` config, and `.gitignore` were left untouched.

## Verification

- **`./mvnw test`** — BUILD SUCCESS, 1 test run, 0 failures. The test log confirms JPA and H2 are fully wired (Hibernate initialized the persistence unit against H2 2.4.240).
- **App startup** — started in about 1 second via `./mvnw spring-boot:run`, and `GET /api/books` returned `HTTP 200`, `Content-Type: application/json`, body `[]`.

One environment note: this shell exports `SERVER_PORT=0`, so when I ran the app here Tomcat bound a random port (I verified the endpoint against that port). The project itself doesn't set any port, so in a normal environment it will serve on the default 8080.

The team can now add entities under `com.example.bookshelf`, create `JpaRepository` interfaces, and inject them into the controller — no further wiring is needed.
```

## claude-haiku-4-5 · boot/000-initializr-parity · sample 1 · idiom_untested

- model: claude-haiku-4-5-20251001 (claude, CLI 2.1.221 (Claude Code))
- duration: 106s, tokens: n/a, cost: $0.1307798
- hidden tests: not run, idiom checks: failed (pre-0.6.0 judge stopped before the tests)
- failure kind: policy_failure
- failure reason: required modern Spring mechanism missing from pom: spring-boot-starter-webmvc
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-claude-haiku-4-5-a1-b2a509a4-6027-4e9c-baa2-d36c8e4e51f0

Agent's closing summary:

```
I'll set up this Spring Boot 4 project from scratch. Let me start by checking the current state and then building out the project structure.Now I'll build the complete project. I'll create the pom.xml with Spring Boot 4 dependencies, then set up the Java source files.Now let me verify the structure and run a build to ensure everything works:Perfect! The project is complete and working. Let me verify the final state:All set! The Spring Boot 4 project is ready. Here's what was created:

✅ **Core Configuration**
- Maven `pom.xml` with Spring Boot 4.0.0, Java 25, and all required dependencies
- Group: `com.example`, Artifact: `bookshelf`, Base package: `com.example.bookshelf`
- Spring Web MVC, Spring Data JPA, H2 in-memory database, Spring Boot Test

✅ **Application Files**
- `BookshelfApplication.java` — standard Spring Boot entry point
- `BookController.java` — REST controller with `GET /api/books` endpoint returning an empty JSON array
- `application.properties` — H2 database configuration ready for entity development
- `BookshelfApplicationTests.java` — standard test class with context load verification

✅ **Build & Runtime**
- Project builds successfully with `./mvnw clean package`
- Tests run and pass with `./mvnw test`
- Application starts and serves the endpoint: `GET /api/books` → `[]`
- Existing Maven wrapper preserved

The team can now add entities, repositories, and business logic immediately. The H2 database is auto-configured and ready for schema generation via JPA.
```

## claude-opus-5 · boot/000-initializr-parity · sample 1 · pass

- model: claude-opus-5 (claude, CLI 2.1.221 (Claude Code))
- duration: 242s, tokens: n/a, cost: $1.3520145000000001
- hidden tests: passed, idiom checks: passed
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-claude-opus-5-a1-48c4f211-9a9c-4055-aa62-55b055cb6b30

Agent's closing summary:

```
I'll start by looking at what's in the repository.Latest GA is **Spring Boot 4.1.0**, and I confirmed from the published POMs that Boot 4 deprecated `spring-boot-starter-web` in favor of `spring-boot-starter-webmvc`, and split test slices into module-specific `-test` starters. Writing the project on those conventions.Now a slice test for the endpoint, then build.Tests pass. Now packaging and running the app for real.Bytecode is major 69 (Java 25) and the app starts cleanly. The curl came back empty — checking whether that's the sandbox blocking loopback.Something in the environment forces `server.port=0` (random port). Let me curl the actual bound port.`SERVER_PORT=0` is set by the environment, not the project. Reading the bound port from the log and hitting it.Done. The project builds, tests green, starts, and serves `GET /api/books` → `200 []`.

## What I set up

**Stack** — I checked Maven Central rather than assuming: latest GA is **Spring Boot 4.1.0** (pulling Spring Framework 7.0.8, Hibernate 7.4.1, Tomcat 11.0.22, JUnit Jupiter 6.0.3, H2 2.4.240). Java 25 via `<java.version>25</java.version>`; verified the compiled classes are major version 69.

**Two Boot 4 conventions worth knowing**, both confirmed from the published POMs, not memory:

- `spring-boot-starter-web` is now explicitly *"deprecated in favor of `spring-boot-starter-webmvc`"* — its own POM description says so. A new project today uses `spring-boot-starter-webmvc`.
- Test slices moved into **module-specific test starters**. `spring-boot-starter-test` alone no longer carries `@WebMvcTest` or the JPA slice; those come from `spring-boot-starter-webmvc-test` and `spring-boot-starter-data-jpa-test`. The pom declares all three at test scope, which is what a fresh Web + Data JPA project gets.

**Files** (`pom.xml` rewritten in place, wrapper untouched):

- `BookshelfApplication.java` — standard `@SpringBootApplication`
- `BookController.java` — `@RestController` on `/api/books`, returns `List.of()`
- `application.properties` — `spring.application.name`, explicit in-memory H2 datasource, `ddl-auto=create-drop`
- `BookshelfApplicationTests` — the standard `contextLoads()`
- `BookControllerTests` — `@WebMvcTest` slice asserting the endpoint returns `[]`, using `MockMvcTester`

## Two things to note

`BookController.findAll()` returns `List<Object>` — a deliberate placeholder, since there's no `Book` entity yet. Change it to `List<Book>` when the team adds one.

The endpoint verification took an extra round trip: your shell has **`SERVER_PORT=0`** exported, so the app binds a random port instead of 8080. That's environmental, not from the project — I left `server.port` out of `application.properties`, as a generated project would. I confirmed the endpoint by reading the bound port from the startup log (`HTTP/1.1 200`, `Content-Type: application/json`, body `[]`). If you want 8080 locally regardless, unset that variable rather than pinning the port in config.

One benign startup warning remains: `spring.jpa.open-in-view is enabled by default`. Initializr projects show this too — I left it alone rather than silently picking a persistence-behavior default for you, but it's worth an explicit decision once entities land.
```

## claude-sonnet-5 · boot/000-initializr-parity · sample 1 · idiom_untested

- model: claude-sonnet-5 (claude, CLI 2.1.221 (Claude Code))
- duration: 186s, tokens: n/a, cost: $0.7027671
- hidden tests: not run, idiom checks: failed (pre-0.6.0 judge stopped before the tests)
- failure kind: policy_failure
- failure reason: required modern Spring mechanism missing from pom: spring-boot-starter-webmvc
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-claude-sonnet-5-a1-dbc208c5-a280-40d7-8f81-47e263c59b47

Agent's closing summary:

```
4.1.1 is still in development (milestone opened Aug 4, 2026), so 4.1.0 is the current GA. That's what I'll use as the parent version. Now let me set up the project.Good, Java 25 (Oracle, LTS) is available via sdkman. I'll build using that. Now let's create the project structure.Now the main application class, controller, application.properties, and test.Now let's build and run tests using Java 25.Tests pass, H2/JPA autoconfigure correctly, Spring Boot 4.1.0 with Java 25. Now let's package and start the app to verify the endpoint.That just launched the nohup wrapper (returns immediately); the actual server needs a moment to boot. Let me check it's up and hit the endpoint.The endpoint works — `GET /api/books` returns `[]` with HTTP 200. Let me check the startup log and then shut it down.Confirmed Boot 4.1.0, Tomcat, real startup. Now stop the running server.The Bookshelf project is set up and verified working:

- **`pom.xml`** — Maven parent `spring-boot-starter-parent:4.1.0` (current GA), Java 25, `com.example:bookshelf`, with `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `h2` (runtime), `spring-boot-starter-test`, and the `spring-boot-maven-plugin`.
- **`BookshelfApplication.java`** — standard `@SpringBootApplication` entry point.
- **`BookController.java`** — `GET /api/books` returning `[]`.
- **`application.properties`** — just `spring.application.name=bookshelf` (H2 auto-configures as an in-memory embedded datasource with no extra config needed, same as a fresh Initializr project).
- **`BookshelfApplicationTests.java`** — the standard `contextLoads()` smoke test.
- Maven wrapper left untouched.

Verified end-to-end using the Java 25 (Oracle) toolchain on the box:
- `./mvnw test` passes (Spring context loads, Hibernate/H2 initialize cleanly).
- `./mvnw package` builds the jar; running it boots Tomcat on Boot 4.1.0 and `curl http://localhost:8099/api/books` returns `[]` with HTTP 200. Process has been stopped afterward.
```
