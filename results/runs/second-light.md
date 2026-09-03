# Run: second-light

Started 2026-08-05T15:54:21.520255Z. 3 of 12 samples passed. Harness 0.2.0+87a8d7e9d450.

## Findings

What this run tested: all 12 agents against boot/000-initializr-parity, one attempt each, $8 cap. Recorded spend was $3.99 (only the Claude adapter reports cost). The run produced 4 real verdicts and 8 infrastructure failures. One of the 8 was mislabeled as a verdict.

**Real verdicts (the model saw the task and the judge measured the work):**

- claude-fable-5: PASSED. Verified 4.1.0 as latest GA against Maven Central, used Boot 4 starter names, $1.43.
- claude-opus-5: PASSED. Most thorough run (288s, $1.60). Probed Maven Central for 4.1.1/4.1.2/4.0.8 to confirm 4.1.0 is newest, added test-slice starters, wrote an extra @WebMvcTest.
- claude-sonnet-5: PASSED. Best cost-to-quality of the passers ($0.83, 155s).
- claude-haiku-4-5: FAILED, and it is a genuine model failure. It built Spring Boot 4.0.0 with the old `spring-boot-starter-web` naming. The mechanism check caught the missing `spring-boot-starter-webmvc`. This is exactly the Boot 4 knowledge gap the eval exists to measure. Haiku's own summary claims success, which shows why hidden checks matter.

**Infrastructure failures (no verdict, excluded from scoring):**

- codex (sol, terra, luna): the output-parsing NPE returned, 0s duration each, so the CLI died before calling the model. Nothing spent. Prime suspect is the awk edit that stripped MCP servers from `~/.codex/config.toml` before the run. Fix: restore the backup (`mv ~/.codex/config.toml.bak ~/.codex/config.toml`), verify with `codex exec` manually, and consider a sterile CODEX_HOME in the harness so host config can never break runs again.
- gemini-2-5-flash and flash-lite: Google retired these models for new API users (404 "no longer available to new users"). The agent configs need current model IDs. Also, the CLI warned that GOOGLE_API_KEY takes precedence over GEMINI_API_KEY; decide which key should bill these runs and unset the other.
- gemini-2-5-pro: the most painful loss of the run. The model worked for 75s and built a correct project. Replaying the exact judge command on a copy of its workspace gives BUILD SUCCESS with all 3 hidden tests passing and parent 4.1.0. The CLI then exited 1, so the SDK discarded the result and no verdict was recorded. The pass was real but unmeasured. Do not hand-edit results; fix the adapter path or retry the attempt once the CLI exit cause is found.
- grok-4-5: the qwen-code CLI (0.21.5) failed to initialize its session against the x.ai endpoint (protocol NPE before any model call). Nothing spent.
- kimi-k3: recorded as a 0% verdict but it is not one. Moonshot rejected the model ID; the Claude CLI exited cleanly with the text "There's an issue with the selected model (kimi-k3)", so the harness judged the untouched workspace and logged a policy_failure in 1s with $0.00. The model never saw the task. Treat this row as an infrastructure failure when reading the scoreboard.

**Harness gap found by this run:** an agent CLI that fails but exits 0 gets scored as a real failure. The kimi row is the proof. A future hashed-harness batch should detect "workspace unchanged plus error-shaped response" and classify it agent_error instead of letting it reach the judge.

**Correction, added after the run:** while chasing the kimi failure we proved (by bytecode inspection) that the agent SDK silently drops the per-agent environment settings, so the sterile CLAUDE_CONFIG_DIR never reached the Claude CLI in this run. The empty sterile directories on disk confirm it. That means the four Claude attempts above ran with the host's own Claude config available, including Dan's Spring skills, and likely billed the Max subscription rather than the bench API key. Treat the three passes and the haiku failure as unverified until re-run under harness 0.3, which enforces the environment at process level and self-tests the mechanism before spending. The infrastructure failure analysis is unaffected.

**What to fix before the next run, in order:** restore the codex config backup and verify the CLI manually; update the two Gemini flash model IDs and settle the GOOGLE_API_KEY vs GEMINI_API_KEY question; find the right Moonshot model ID for Kimi and test it with a direct CLI call before spending; investigate the gemini CLI exit-1 and the qwen-code session failure against x.ai.

## claude-fable-5 · boot/000-initializr-parity · sample 1 · pass

- model: claude-fable-5 (claude, CLI 2.1.221 (Claude Code))
- duration: 167s, tokens: n/a, cost: $1.4311555
- hidden tests: passed, idiom checks: passed
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-claude-fable-5-a1-ef300ad2-8085-45c9-93f4-a2bd2abffa6f

Agent's closing summary:

```
I'll start by looking at what's already in the repository.The search index looks stale. Let me check the authoritative maven-metadata.xml directly.All files are in place. Now running the test suite with the wrapper.The project is set up and verified. It builds, tests pass, the app starts, and `GET /api/books` returns `200` with `[]` as `application/json` on a current Spring Boot 4 stack.

What I created, written by hand (no generator used), keeping the existing Maven wrapper untouched:

- **`pom.xml`**: parent `spring-boot-starter-parent` **4.1.0**, the latest GA on Maven Central (the 4.x line runs 4.0.0 through 4.0.7 plus 4.1.0, so a new project today starts on 4.1.0). Group `com.example`, artifact `bookshelf`, Java 25. Dependencies follow Boot 4 conventions: `spring-boot-starter-webmvc` (Boot 4 renamed the old `spring-boot-starter-web`), `spring-boot-starter-data-jpa`, `com.h2database:h2` at runtime scope, and `spring-boot-starter-test` at test scope, with the `spring-boot-maven-plugin` in the build.
- **`src/main/java/com/example/bookshelf/BookshelfApplication.java`**: standard `@SpringBootApplication` class with the `main` method.
- **`src/main/java/com/example/bookshelf/BookController.java`**: `@RestController` mapped to `/api/books`, returning an empty list for now.
- **`src/main/resources/application.properties`**: `spring.application.name=bookshelf`. H2 needs no config; Boot auto-creates the in-memory datasource.
- **`src/test/java/com/example/bookshelf/BookshelfApplicationTests.java`**: the standard `@SpringBootTest` context-loads test.

Verification results:

- `./mvnw test` passes (1 test, 0 failures). The logs show the full JPA stack coming up: HikariCP pool, in-memory H2 (`jdbc:h2:mem:...`), Hibernate ORM 7.4.1 with `H2Dialect`.
- `./mvnw spring-boot:run` starts cleanly on port 8080, and `GET /api/books` returns `HTTP 200`, `Content-Type: application/json`, body `[]`. I stopped the app after the check.

The team can start adding `@Entity` classes and Spring Data repositories right away; the datasource and JPA are already wired. One heads-up for later: in Boot 4 the H2 web console no longer comes with the raw H2 jar, so if anyone wants it for local debugging, add the `spring-boot-starter-h2-console` starter.
```

## claude-haiku-4-5 · boot/000-initializr-parity · sample 1 · idiom_untested

- model: claude-haiku-4-5-20251001 (claude, CLI 2.1.221 (Claude Code))
- duration: 93s, tokens: n/a, cost: $0.13195020000000002
- hidden tests: not run, idiom checks: failed (pre-0.6.0 judge stopped before the tests)
- failure kind: policy_failure
- failure reason: required modern Spring mechanism missing from pom: spring-boot-starter-webmvc
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-claude-haiku-4-5-a1-2155047a-f843-4215-a1d4-7421c4158b2f

Agent's closing summary:

```
I'll set up a fresh Spring Boot 4 project from scratch. Let me start by checking the current state and then build out the complete project structure.Now I'll build out the complete Spring Boot 4 project. Let me start with the POM configuration:Now I'll create the directory structure and Java files:Now let me verify the project builds and runs tests:Now let me start the application and verify the endpoint works:Perfect! Let me verify the JAR runs standalone:Perfect! Spring Boot 4 project is fully set up and verified. Here's what's been created:

**Project Summary:**
- Spring Boot 4.0.0 (GA version) with Spring Framework 6, Tomcat 11, and Hibernate 7
- Java 25+ configured (tested with Java 26)
- Maven build with preserved wrapper scripts
- Group: `com.example`, Artifact: `bookshelf`, Package: `com.example.bookshelf`

**Configured Components:**
- Spring MVC with REST controller: `GET /api/books` endpoint (returns `[]`)
- Spring Data JPA with entity support (config/entity packages ready for additions)
- H2 in-memory database configured (jdbc:h2:mem:testdb)
- Hibernate with automatic schema creation (ddl-auto=create-drop)
- H2 console enabled for debugging

**Verified Working:**
- `./mvnw test` passes with standard Spring Boot test setup
- `./mvnw clean package` builds executable JAR
- `java -jar target/bookshelf-0.0.1-SNAPSHOT.jar` starts and serves the endpoint
- Endpoint curl: `[] ` response confirmed

The team can now start adding entities to the `entity` package and repositories to the `config`/repository packages. The database is ready to accept JPA entities immediately.
```

## claude-opus-5 · boot/000-initializr-parity · sample 1 · pass

- model: claude-opus-5 (claude, CLI 2.1.221 (Claude Code))
- duration: 288s, tokens: n/a, cost: $1.5951745000000002
- hidden tests: passed, idiom checks: passed
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-claude-opus-5-a1-ab789d06-ca44-4fe9-8957-41ca7c34485f

Agent's closing summary:

```
I'll start by looking at what's actually in the repository.Everything checks out. Latest GA is Spring Boot 4.1.0 (Boot 4 uses modular auto-config, so the starters are `spring-boot-starter-webmvc` and the matching `-test` slices). Writing the project now.Now let's build and run the tests.Bytecode is major version 69 (Java 25). Now let's package it and verify the running app serves the endpoint.`GET /api/books` returns `200` with `[]` and `Content-Type: application/json`. Let me check the startup log for warnings, then shut it down.That background "failure" is just my `pkill` stopping the app. The startup log had zero warnings or errors. Final clean verification:Done. The project builds, tests pass, the app starts, and the endpoint serves `[]`.

## What I set up

**`pom.xml`** — `spring-boot-starter-parent` **4.1.0**, the current GA release. I confirmed this against Maven Central directly: `4.1.1`, `4.1.2`, and `4.0.8` all return 404, so 4.1.0 is the newest. Java 25 via `<java.version>25</java.version>`, which the parent maps to `maven.compiler.release`.

**Source files** (all in `com.example.bookshelf`):
- `BookshelfApplication.java` — standard `@SpringBootApplication` with `SpringApplication.run`
- `BookController.java` — `@RestController` on `/api/books`, `@GetMapping` returning an empty list
- `application.properties` — app name, in-memory H2 URL, `ddl-auto=create-drop`, `open-in-view=false`
- `BookshelfApplicationTests.java` — the standard `@SpringBootTest` `contextLoads` test
- `BookControllerTests.java` — a `@WebMvcTest` slice asserting the endpoint returns `[]`

## Boot 4 specifics worth knowing

Boot 4 split the old monolithic auto-configure jar into modules, so several starter names changed. I verified each one against the actual 4.1.0 jars rather than assuming:

- `spring-boot-starter-web` is now **`spring-boot-starter-webmvc`**. It brings in Tomcat and Jackson.
- Test slice annotations moved out of `spring-boot-test-autoconfigure`. `@WebMvcTest` now lives at `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`, in `spring-boot-starter-webmvc-test`. So I added `spring-boot-starter-webmvc-test` and `spring-boot-starter-data-jpa-test` alongside `spring-boot-starter-test`. Without them the team cannot write `@WebMvcTest` or `@DataJpaTest` slices at all.

The stack resolved to Hibernate 7.4.1, H2 2.4.240, and JUnit Jupiter 6.0.3.

## Verification

- `./mvnw clean test` → `Tests run: 2, Failures: 0, Errors: 0`, BUILD SUCCESS
- Bytecode is class file major version 69, which is Java 25
- Packaged jar starts in 1.2s with a clean log, no warnings and no errors
- `curl http://localhost:8080/api/books` → `200`, `Content-Type: application/json`, body `[]`
- Maven wrapper files are untouched (original timestamps intact)

## Two judgment calls

`findAll()` returns `List<Object>`, not `List<Book>`. You asked for an empty array and for JPA to be ready for entities, which implies no `Book` entity exists yet. Inventing one would widen the scope, so I left a comment marking where the type changes once you add it.

I skipped the H2 web console. In Boot 4 it needs a separate module, so setting `spring.h2.console.enabled=true` on its own would do nothing. Add `spring-boot-h2console` if you want it.

One note on the test output: you will see JVM warnings about a dynamically loaded byte-buddy agent. That comes from Mockito inside `spring-boot-starter-test` on modern JDKs. It is not caused by anything in this setup, and a freshly generated project shows the same thing.
```

## claude-sonnet-5 · boot/000-initializr-parity · sample 1 · pass

- model: claude-sonnet-5 (claude, CLI 2.1.221 (Claude Code))
- duration: 155s, tokens: n/a, cost: $0.8298398999999999
- hidden tests: passed, idiom checks: passed
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-claude-sonnet-5-a1-d316aa05-c6d9-4d36-9371-7f39300470fe

Agent's closing summary:

```
I'll start by checking the current state of the repository.Good, we have internet access confirmed. Maven Central confirms the latest GA is `4.1.0`. That matches the local cache too, so I'll use Spring Boot 4.1.0 as the parent version.

Now let me check what starter artifact names look like in Boot 4 (since Boot 4 renamed `spring-boot-starter-web` to `spring-boot-starter-webmvc`), and check Java 25 compatibility.There's already a `.gitignore`. Let me check it so I don't duplicate/conflict.Good, Jackson and Tomcat are pulled in transitively as expected. Now let's write the pom.xml.Now let's create the source tree: main application class, controller, resources, and test class.Now let's build and test the project.The context test passed and confirms H2 auto-configured correctly, JPA wired, Java 26 runtime (compiled at release 25) works fine. Let's confirm the test actually succeeded (BUILD SUCCESS) and no failures reported.BUILD SUCCESS. Now let's start the app and verify the endpoint actually serves an empty JSON array.The app is running on port 8080. Let's hit the endpoint to confirm.The endpoint returns `200 OK` with `[]` as expected. Now let's shut down the running app cleanly.The app stopped cleanly. Let's do a final review of the project layout.The bookshelf project is fully set up and verified:

- **`pom.xml`**: Spring Boot 4.1.0 (current GA, confirmed via Maven Central), Java 25, `com.example:bookshelf`, with `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `h2` (runtime), `spring-boot-starter-test`, and the Spring Boot Maven plugin.
- **`BookshelfApplication.java`**: standard `@SpringBootApplication` main class.
- **`BookController.java`**: `GET /api/books` returning `[]`.
- **`application.properties`**: just `spring.application.name=bookshelf`, letting Boot auto-configure the embedded H2 datasource.
- **`BookshelfApplicationTests.java`**: standard `contextLoads()` smoke test.

Verified end-to-end: `./mvnw clean test` passes (BUILD SUCCESS, context loads with H2 + JPA wired), and `./mvnw spring-boot:run` starts the app, with `curl http://localhost:8080/api/books` returning `200 OK` and body `[]`. The existing `.mvn` wrapper and `.gitignore` were left untouched.
```

## codex-gpt-5-6-luna · boot/000-initializr-parity · sample 1 · agent_error

- model: gpt-5.6-luna (codex, CLI codex-cli 0.146.0)
- duration: 0s, tokens: n/a, cost: n/a
- failure kind: agent_error
- failure reason: NullPointerException: Cannot invoke "java.util.function.Supplier.get()" because "defaultObjectSupplier" is null
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-codex-gpt-5-6-luna-a1-3a9ae0a3-e22e-4770-bf14-e890351a5bb6

## codex-gpt-5-6-sol · boot/000-initializr-parity · sample 1 · agent_error

- model: gpt-5.6-sol (codex, CLI codex-cli 0.146.0)
- duration: 0s, tokens: n/a, cost: n/a
- failure kind: agent_error
- failure reason: NullPointerException: Cannot invoke "java.util.function.Supplier.get()" because "defaultObjectSupplier" is null
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-codex-gpt-5-6-sol-a1-78eee8f0-a617-4cc0-a72b-e050b1d8ff8d

## codex-gpt-5-6-terra · boot/000-initializr-parity · sample 1 · agent_error

- model: gpt-5.6-terra (codex, CLI codex-cli 0.146.0)
- duration: 0s, tokens: n/a, cost: n/a
- failure kind: agent_error
- failure reason: NullPointerException: Cannot invoke "java.util.function.Supplier.get()" because "defaultObjectSupplier" is null
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-codex-gpt-5-6-terra-a1-be6ef2bf-0b6c-417b-ba27-d550fd88e60c

## gemini-2-5-flash · boot/000-initializr-parity · sample 1 · agent_error

- model: gemini-2.5-flash (gemini, CLI 0.1.13)
- duration: 1s, tokens: n/a, cost: n/a
- failure kind: agent_error
- failure reason: NullPointerException: Cannot invoke "java.util.function.Supplier.get()" because "defaultObjectSupplier" is null
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-gemini-2-5-flash-a1-a9192342-73a0-410e-9de4-1a640bc3cad4

## gemini-2-5-flash-lite · boot/000-initializr-parity · sample 1 · agent_error

- model: gemini-2.5-flash-lite (gemini, CLI 0.1.13)
- duration: 0s, tokens: n/a, cost: n/a
- failure kind: agent_error
- failure reason: NullPointerException: Cannot invoke "java.util.function.Supplier.get()" because "defaultObjectSupplier" is null
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-gemini-2-5-flash-lite-a1-1bb8d544-01b7-4c62-9482-11d58869de0d

## gemini-2-5-pro · boot/000-initializr-parity · sample 1 · agent_error

- model: gemini-2.5-pro (gemini, CLI 0.1.13)
- duration: 75s, tokens: n/a, cost: n/a
- failure kind: agent_error
- failure reason: NullPointerException: Cannot invoke "java.util.function.Supplier.get()" because "defaultObjectSupplier" is null
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-gemini-2-5-pro-a1-9ac11787-d959-40ef-ae53-2a58b266be69

## grok-4-5 · boot/000-initializr-parity · sample 1 · agent_error

- model: grok-4.5 (qwen-code, CLI 0.21.5)
- duration: 0s, tokens: n/a, cost: n/a
- failure kind: agent_error
- failure reason: NullPointerException: Cannot invoke "java.util.function.Supplier.get()" because "defaultObjectSupplier" is null
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-grok-4-5-a1-03ac1eab-aecf-4483-9843-600ac305c5e1

## kimi-k3 · boot/000-initializr-parity · sample 1 · idiom_untested

- model: kimi-k3 (claude, CLI 2.1.221 (Claude Code))
- duration: 1s, tokens: n/a, cost: $0.0
- hidden tests: not run, idiom checks: failed (pre-0.6.0 judge stopped before the tests)
- failure kind: policy_failure
- failure reason: required modern Spring mechanism missing from pom: spring-boot-starter-parent</artifactId>(?:(?!</parent>).)*?<version>\s*4\.
- workspace (until temp cleanup): /var/folders/bx/4zdlmchj5g9dnt7brcv518m00000gn/T/spring-evals-runs/boot-000-initializr-parity-kimi-k3-a1-5075a7e7-aecf-48f6-a55b-b56b824ca6b9

Agent's closing summary:

```
There's an issue with the selected model (kimi-k3). It may not exist or you may not have access to it.
```
