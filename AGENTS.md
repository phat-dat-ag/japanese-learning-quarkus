# AGENTS.md

## 1. Project context

- One Maven application: `com.japaneselearning:backend`, Java 17, Quarkus 3.38.2; wrapper Maven 3.9.16.
- Feature packages: vocabulary/lessons/Admin editing, flashcard queries, and Kanji Context Quiz administration/gameplay/history/progress.
- Angular and .NET authentication/user management are external. This backend verifies .NET-issued JWTs using public JWKS.
- No API Gateway or Compose orchestration is implemented here. Do not invent modules, forwarding routes, or independent feature services.
- Use [README.md](README.md) for setup, configuration, Docker, and detailed commands; use source as the authority for behavior.

## 2. Repository navigation

Paths below are relative to `src/main/java/com/japaneselearning/` unless stated otherwise.

| Area | Location / responsibility |
| --- | --- |
| Vocabulary | `vocabulary/{resource,service,repository,entity,dto,importer,logging}`; `vocabulary/admin/{resource,service,dto}` |
| Flashcards | `flashcard/{resource,service,repository,dto}`; service assemblers construct detail DTOs |
| Quizzes | `quiz/{domain,entity,repository,service}`; `quiz/admin/{resource,service,repository,dto}`; `quiz/player/{resource,service,dto}` |
| Shared contracts | `common/{resource,response,exception,exception/handler,web}` |
| Operations/configuration | `common/{config,health,metrics,logging}` |
| Security | Resource annotations and root-relative `src/main/resources/application.properties`; no production security package |
| API/migrations | Root-relative `src/main/resources/META-INF/openapi.yaml`, `src/main/resources/db/migration/` |
| Tests | Root-relative `src/test/java/com/japaneselearning/`, `src/test/resources/`; security package includes JWKS fixtures |
| Build/docs | Root `pom.xml`, `.mvn/wrapper/`, `Dockerfile`, `docs/`; no child Maven modules |

## 3. Token-efficient exploration

- Understand the task and identify its owning feature before searching. Reuse context already inspected in this session.
- Start with filenames/package structure; read one or two closest implementations and only their relevant dependencies.
- Use targeted `rg`/`rg --files` searches by symbol, endpoint, DTO, table, or migration; bound searches to relevant source directories.
- Exclude `target/`, generated sources, other build output, logs, and dependency caches unless the task specifically requires them.
- Read relevant sections instead of dumping large files. Do not reread unchanged files or preload every Resource/Service/Repository.
- Batch independent discovery where useful; expand scope only when evidence is insufficient. Never trade correctness/security for fewer tokens.

## 4. Java and Quarkus conventions

- Keep feature-first packages and established suffixes: `Resource`, `Service`, `Repository`, `Request`, `Response`.
- Use PascalCase types, camelCase members, and UPPER_SNAKE_CASE constants; match surrounding four-space indentation and brace style.
- No `.editorconfig` or formatter plugin is currently configured. Preserve local import grouping/order; do not impose a new style or reformat unrelated code.
- DTOs commonly use records; import DTOs also use classes. Entities use Jakarta persistence mappings, often public fields. Follow the nearest equivalent.
- Prefer constructor injection with explicit final dependencies; production services/repositories commonly use `@ApplicationScoped`.
- Mapping is handwritten in services/assemblers. Do not introduce Lombok, MapStruct, or service interfaces merely for stylistic uniformity.
- Preserve nullable-field semantics and Jackson non-null serialization; handle missing repository results explicitly.
- Reuse existing property expressions and `@ConfigProperty`; preserve `RequiredConfiguration`/`SafeConfigurationInterceptor` validation.

## 5. Clean Code and SOLID

- Keep methods cohesive, classes focused, and names descriptive. Separate HTTP, business, persistence, and response responsibilities.
- Apply DRY, KISS, YAGNI, and SOLID pragmatically: use guard clauses, avoid deep nesting, and name meaningful constants.
- Prefer composition where useful, honor interface contracts, and avoid circular dependencies or unnecessary coupling.
- Introduce an abstraction/interface only for a concrete requirement; avoid speculative frameworks and premature optimization.
- Comments explain intent or constraints, not obvious code.

## 6. Reuse and consistency

- Before adding a DTO, validator, exception, helper, query, or abstraction, search for an existing equivalent in the owning feature and `common`.
- Extend suitable existing behavior rather than duplicate it; keep one-off helpers local instead of creating generic utilities.
- Keep domain logic in its feature; place genuinely shared infrastructure in `common`. Reuse the current stack before adding dependencies.
- Preserve existing contracts and semantics; do not silently replace project patterns with preferred alternatives.

## 7. Service boundaries

- Flow: Resource → Service → Repository → MySQL. Feature packages share one datasource and deployment.
- Flashcards consume vocabulary data; quizzes reference vocabulary/examples and own session/progress data. Preserve those ownership relationships.
- .NET owns login, users, and signing keys. Do not add local login or direct access to the authentication service's database.
- No established general remote-client retry/timeout framework or Gateway routing exists here. Inspect actual integration code before extending it.
- Any required remote call must use explicit environment-specific configuration and bounded failure handling; do not add distributed transactions without architectural justification.

## 8. Reactive execution and CDI

- Preserve Mutiny `Uni`/`Multi` and Hibernate Reactive Panache. Never substitute blocking Hibernate/JDBC in request flows.
- Never use blocking waits, sleeps, file I/O, or synchronous network calls on event-loop threads; follow existing import readers' worker-offloading patterns.
- Use `@WithSession`, `@WithTransaction`, or existing reactive Panache transaction patterns at business boundaries; preserve rollback semantics.
- Compose dependent persistence operations in the reactive chain; do not fire-and-forget or parallelize work sharing a reactive session.
- Avoid mutable request state in application-scoped beans. Do not manually instantiate CDI services in production; plain unit tests may construct them.

## 9. REST contracts

- Keep Resources thin; use `BaseResource`, `ApiResponse`, `ErrorResponse`, `ResponseMeta`, and established pagination metadata where applicable.
- Preserve versioned `/api/v1/...` routes and the existing unversioned `/api/vocabularies` import API; do not rename for consistency alone.
- Use request/response DTOs, Jakarta boundary validation, existing status/error codes, and centralized mappers. Keep business validation in services.
- Match existing pagination/filter defaults, bounds, and ordering; do not expose persistence entities.
- Update OpenAPI alongside contract changes. Static `META-INF/openapi.yaml` merges with annotations; `vocabulary.admin` is excluded from scanning.
- Preserve global `bearerAuth` documentation and intentional nullability. Runtime authorization remains authoritative.

## 10. Security

- Preserve OIDC bearer-service mode, public JWKS verification, and disabled discovery/introspection/Dev Services.
- Preserve RS256, issuer/audience checks, required subject/expiration, future-`nbf` rejection, and zero clock grace; `iat` is intentionally not required.
- Roles come from case-sensitive `role`; default REST access is User/Admin. Explicit Admin restrictions protect imports, lesson writes, and Admin APIs.
- Preserve subject-based quiz ownership checks; never trust a caller-supplied user identifier in place of authenticated `sub`.
- Keep missing/invalid-token 401 distinct from insufficient-role 403. Never bypass security to simplify implementation or tests.
- Never hardcode or expose credentials, JWTs, private keys, or secrets. Use existing configuration and in-memory test signing fixtures.
- Do not unintentionally broaden documentation/health/metrics exposure or alter security while changing routes.

## 11. Database and migrations

- Use reactive repositories and targeted queries; avoid N+1 access, unnecessary eager loading, and loading whole datasets for filtering.
- Preserve database constraints, indexes, locking/version checks, transaction scope, and immutable quiz snapshot behavior.
- Schema generation is disabled. Flyway SQL lives in `src/main/resources/db/migration/` and runs externally; no application Flyway integration exists.
- Inspect relevant migrations first; add a new `V<next>__<description>.sql` for required schema/seed changes. Never alter applied migrations unless explicitly instructed.
- Current migrations V1-V8 target MySQL, including MySQL 8 collation/CHECK behavior. Do not assume MariaDB compatibility.
- Lessons are application data, not migration seeds. Preserve import assignment validation and atomic vocabulary batch behavior.

## 12. Errors, logging, and observability

- Reuse `BusinessException`, `ValidationException`, `ResourceNotFoundException`, `ConflictException`, and centralized handlers as appropriate.
- Translate expected failures at the layer that understands them; preserve useful internal causes without leaking database details or stack traces to clients.
- Do not swallow failures, catch only to rethrow unchanged, or duplicate failure logs across layers.
- Use existing JBoss Logger conventions, trace/correlation context, and `LogVocabularyOperation` where applicable; avoid payloads and noisy per-item logs.
- Preserve the OIDC provider ERROR override: lower-level verification messages can expose tokens.
- Retain health error redaction, bounded metric labels, asynchronous logging, and off-thread archive retention; logs are not a durable audit trail.

## 13. Production quality

- Validate untrusted input and handle relevant null/empty, boundary, concurrency, and failure cases.
- Keep configuration external, resources correctly scoped/closed, queries bounded, and retries justified by existing failure semantics.
- Preserve compatibility and observability; add complexity only when it solves a concrete correctness, security, or maintainability problem.
- Root `Dockerfile` is the source-build Java 17 path. Other Docker templates require artifacts excluded by current `.dockerignore`; do not assume they work unchanged.

## 14. Testing and verification

- Follow JUnit Jupiter, `@QuarkusTest`, REST Assured, handwritten doubles, and CDI test alternatives; Mockito is not a configured dependency.
- Add/update focused deterministic tests for changed behavior, covering success, expected failure, and relevant edges without duplicating implementation.
- Default tests use offline persistence/doubles and `JwksTestResource`; do not require live .NET or MySQL unnecessarily.
- Run from the root with `./mvnw` (PowerShell: `.\mvnw.cmd`): `test "-Dtest=LessonServiceTest"` for a focused class, `test` for the default suite.
- Broader verification: `./mvnw verify "-Dquarkus.http.test-port=0"`; compile/package when relevant with `./mvnw compile` or `./mvnw package`.
- Real DB tests require `-Dvocabulary.mysql.tests=true`, `-Dvocabulary.mysql.url=mysql://host:port/vocabulary_import_test`, and `-Dvocabulary.mysql.password=<test-password>`.
- Use only a disposable, externally migrated test database through the current migration version; the test profile fixes username `root`. Tests mutate data.
- MySQL tests run as Surefire `*Test` classes; `skipITs=false` alone does not enable them. No packaged `@QuarkusIntegrationTest` tests or coverage plugin are configured.
- Run targeted tests first; broaden for cross-cutting changes or unresolved risk. Diagnose failures before changing expectations; report pre-existing failures separately.

## 15. Minimal changes and Git safety

- Before edits, run `git branch --show-current` and `git status --short`; preserve all unrelated user changes.
- Modify only task-relevant files. No incidental cleanup, mass formatting, public renaming, dependency upgrades, extra docs, or architecture changes.
- Change schema, security, and public contracts only when required; keep changes small and reviewable.
- Do not commit, push, pull, switch branches, rebase, reset, clean, stash, or discard changes unless explicitly requested.
- Never run destructive commands without explicit authorization.

## 16. Agent workflow

1. Understand requirements, identify the owning feature, and inspect Git state.
2. Locate relevant files, closest patterns, and reusable components; plan the smallest correct change.
3. Implement within the established layers and add/update behavior tests.
4. Run focused verification; inspect `git diff --check`, `git diff --stat`, and the relevant diff for unintended changes.
5. Remove task-created debug artifacts and report the actual outcome.
- Ask only when ambiguity materially affects correctness, security, compatibility, schema, or architecture; otherwise proceed using existing conventions.

## 17. Communication

- Keep updates concise and useful; avoid repeated plans, task restatements, basic tutorials, large file dumps, and speculative advice.
- Final response: changed behavior/files, checks actually executed and results, plus important risks or unresolved questions.
- Never claim unexecuted tests/builds passed. Identify missing infrastructure or unverified integration behavior explicitly.

## 18. Prohibited shortcuts

- Do not invent functionality, duplicate business logic, add unnecessary interfaces/frameworks, or refactor unrelated code.
- Never bypass authorization, leak secrets, suppress unexplained failures, block reactive request execution, or weaken constraints to make tests pass.
- Token efficiency never overrides correctness, security, task scope, or honest verification.
