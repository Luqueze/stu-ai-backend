# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Build system is Gradle (Kotlin DSL) via the wrapper — always use `./gradlew` (or `gradlew.bat` on Windows), never a globally installed `gradle`.

```bash
./gradlew build                 # build all modules (compile + test + jar/bootJar)
./gradlew build -x test         # build without running tests
./gradlew clean build           # clean rebuild
./gradlew projects              # list all modules and their descriptions
./gradlew test                  # run all tests in all modules
./gradlew :exam-service:test    # run tests for a single module
./gradlew :exam-service:test --tests "com.aiexam.examservice.SomeTest"   # run a single test class
./gradlew :exam-service:test --tests "com.aiexam.examservice.SomeTest.someMethod"  # run a single test method
./gradlew :api-gateway:bootRun  # run a single Spring Boot service locally
./gradlew :exam-service:dependencies   # inspect resolved dependency tree for a module
```

Module task paths mirror the flat project names declared in `settings.gradle.kts` (e.g. `:auth-service`, `:common-events`), **not** their physical folder paths (`services/auth-service`, `shared/common-events`).

## Architecture

This is a Java 21 / Spring Boot 3.3.5 Gradle multi-module monorepo for an AI-assisted exam platform, structured as independently deployable services around a shared event-contract module.

### Module layout vs. Gradle project graph

Physical directories are organized as `services/*` and `shared/*`, but `settings.gradle.kts` maps each to a **flat** Gradle project name (`:api-gateway`, `:auth-service`, `:exam-service`, `:ai-generator-service`, `:common-events`) via explicit `projectDir` assignment rather than colon-nested `include("services:api-gateway")`. This is deliberate: nested includes would create empty intermediate placeholder projects (`:services`, `:shared`) that Gradle's `subprojects {}` block would also configure. When adding a new module, follow the same pattern in `settings.gradle.kts` — add a flat `include(...)` entry plus a `project(":name").projectDir = file(...)` line.

### Services

- **api-gateway** — Spring Cloud Gateway + Actuator. Entry point / routing layer.
- **auth-service** — Web, Security, Data JPA, Validation. Owns authentication/authorization.
- **exam-service** — Web, Data JPA, Data Redis. Depends on `common-events`.
- **ai-generator-service** — Web + Spring AI's OpenAI starter (`spring-ai-openai-spring-boot-starter`). Depends on `common-events`. Handles AI-driven question/exam generation.
- **common-events** (`shared/common-events`) — plain `java-library`, deliberately has **no** Spring Boot runtime dependency. Holds shared DTOs/records (event contracts) consumed by `exam-service` and `ai-generator-service`. Keep it dependency-light since it's a compile-time dependency of multiple services.

Package convention: `com.aiexam.<servicenamewithoutdashes>` (e.g. `com.aiexam.examservice`, `com.aiexam.aigeneratorservice`).

`infra/local` and `infra/terraform` exist as placeholders for local-dev infra (e.g. docker-compose) and IaC, respectively — currently empty.

### Dependency version management

Versions are centralized in `gradle/libs.versions.toml` (Gradle version catalog): `springBoot` (3.3.5), `springCloud` (2023.0.3), `springAi` (1.0.0-M6).

BOMs are **not** imported via the `io.spring.dependency-management` plugin — this project intentionally uses Gradle's native `platform()` mechanism instead. Each service module that needs managed versions must explicitly declare it in its own `build.gradle.kts`, e.g.:

```kotlin
dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(platform(libs.spring.cloud.dependencies))  // only where Spring Cloud is used
    implementation(platform(libs.spring.ai.bom))               // only where Spring AI is used
}
```

`spring-ai` is currently pinned to a milestone release (`1.0.0-M6`), so the root `build.gradle.kts` adds `https://repo.spring.io/milestone` as a repository on `allprojects`. Bumping `springAi` to a GA release should remove the need for that repository and also renames the OpenAI starter artifact (`spring-ai-openai-spring-boot-starter` → `spring-ai-starter-model-openai` from GA onward) — update `ai-generator-service/build.gradle.kts` accordingly if upgrading.

### Root build conventions

`build.gradle.kts` at the root applies to all modules via `allprojects`/`subprojects`:
- `group`/`version` set on `allprojects`.
- `subprojects` apply the `java` plugin, set the toolchain to Java 21, and configure `tasks.withType<Test> { useJUnitPlatform() }`.
- Individual modules only need to declare their own `plugins {}` (e.g. `alias(libs.plugins.spring.boot)`) and `dependencies {}` — Java/toolchain/test-runner setup is inherited.

## Coding Conventions

### Controllers — no try/catch, use centralized exception handling

Controllers must stay thin and never contain `try/catch` blocks. Business/validation errors are
thrown as exceptions and resolved centrally via Spring's exception-handling mechanism.

- Each service that exposes REST endpoints defines its **own local**
  `GlobalExceptionHandler` class (e.g. `com.aiexam.examservice.exception.GlobalExceptionHandler`)
  annotated with `@RestControllerAdvice`, with `@ExceptionHandler` methods mapping domain
  exceptions to HTTP responses. This is per-service, not shared via `common-events`.
- Domain-specific exceptions live under an `exception` package in each service
  (e.g. `ExamNotFoundException`, `InvalidExamStateException`), extending `RuntimeException`.
  Do not throw raw `Exception`/`RuntimeException` — always create/reuse a typed exception.
- Error responses use a consistent DTO shape (status, message, timestamp, path) across services,
  even though each `GlobalExceptionHandler` is implemented locally — keep the shape aligned by
  convention, not by shared code.
- Validation errors (`MethodArgumentNotValidException`, `ConstraintViolationException`, etc.)
  are also handled in the service's `GlobalExceptionHandler`, not in the controller method.
- Controllers should read as: validate input via `@Valid`/method signature → delegate to
  service → return response. No defensive try/catch around service calls.

### Entity construction — Builder pattern (Lombok)

New entity instances (JPA `@Entity` classes) must use Lombok's `@Builder` rather than public
no-arg constructors + setters or telescoping constructors. **This applies to `@Entity` classes
only** — see the next section for DTOs and event contracts, which use Java `record` instead, not
Lombok `@Builder`.

- Standard Lombok combo for entities:
  `@Builder`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)` (satisfies JPA's required
  no-arg constructor without exposing it publicly), and `@AllArgsConstructor` (required by
  `@Builder` alongside the protected no-arg constructor).
- Avoid public setters where possible; prefer building a new instance via `.builder()...build()`
  or adding intention-revealing update methods on the entity for mutation, instead of exposing
  broad setters.
- Applies to entity classes across all services (`auth-service`, `exam-service`,
  `ai-generator-service`). Does **not** apply to `common-events` (see below).

### DTOs, Requests e Event Contracts — Java 21 Records

DTOs and event contracts are immutable and modeled with native Java `record`, not `@Entity`
or Lombok.

- **DTOs and events are immutable**: use native Java `record` for all Request/Response DTOs and
  for event contracts in `common-events`.
- Do not use `@Entity` or Lombok (`@Builder`, `@Data`, etc.) in `common-events` — this keeps the
  module dependency-light and consistent with its "plain `java-library`, no Spring Boot runtime
  dependency" rule (see Architecture above).
- Input validation on DTOs uses `jakarta.validation.constraints.*` directly on the record's
  canonical constructor parameters:
  ```java
  public record CreateExamRequest(
      @NotBlank(message = "Theme is required") String theme,
      @Min(value = 1, message = "At least 1 question required") int questionCount
  ) {}
  ```
- Records used purely as internal service DTOs (not shared contracts) may live in an `dto`
  package per service instead of `common-events`, but still use `record`, not classes.

### Mensageria & Integração com IA — Async Processing

AI-driven question/exam generation is **always asynchronous** — never a synchronous, blocking
call from a controller.

- `exam-service` publishes a request event to the queue and immediately returns `PENDING`
  status (HTTP `202 Accepted`) with the exam ID — it does not wait for the AI response inline.
- `ai-generator-service` consumes that event, calls the LLM using Structured Outputs (JSON
  Schema) for the response shape, and publishes a completion event.
- `exam-service` listens for the completion event and transitions the exam's state to `READY`.
- **Timeout/quota handling**: `ai-generator-service` must configure retry policies and a
  dead-letter queue (DLQ) for failures calling the AI API — do not let a failed LLM call silently
  drop the request.

### Persistência & Cache — Database per Service

- **Database isolation**: `auth-service` and `exam-service` each own separate PostgreSQL
  schemas/databases. No service accesses another service's tables directly — cross-service data
  needs go through events (`common-events`) or API calls, never direct DB access.
- **Session & timer state**: `exam-service` manages the active exam session's timer and other
  volatile session state using **Redis**, with a TTL aligned to the exam's duration.
- **Migrations**: production/dev schema versioning is managed via **Flyway**
  (`src/main/resources/db/migration`) — no manual DDL, no `hibernate.ddl-auto=update` in
  non-local environments.

### Docker & Infraestrutura

- **Multi-stage Dockerfiles**: use `eclipse-temurin:21-jdk-jammy` for the build stage and
  `eclipse-temurin:21-jre-jammy` (or an alpine/distroless variant) for the final runtime image —
  never ship the JDK image to production.
- Containers must **not** run as `root`; create and use an unprivileged user (`spring:spring`).
- Sensitive environment variables (API keys, database passwords) must be injected via `ENV`/
  secrets at runtime — never hardcoded in the `Dockerfile` or committed to the repo.

### API documentation — OpenAPI / Swagger

All REST endpoints must be documented with `springdoc-openapi` (Swagger UI/OpenAPI 3).

- Every endpoint gets a `@Operation(summary = "...")` with a **short description, max 15 words**.
  Keep it action-oriented (e.g. `"Creates a new exam for the given course"`), not a restatement
  of the method/URL.
- No verbose `description` blocks by default — the 15-word `summary` is the standard; only add
  a longer `description` if the endpoint has non-obvious behavior worth documenting.
- Request/response DTOs should rely on field-level annotations (`@Schema`, Bean Validation
  annotations) for documentation rather than duplicating explanations in the endpoint summary.
- `springdoc-openapi` dependency/version should be added to `gradle/libs.versions.toml` and
  declared per web-facing service (`api-gateway`, `auth-service`, `exam-service`,
  `ai-generator-service`) the same way other managed dependencies are declared.