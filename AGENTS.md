# AGENTS.md

## Project Overview

This repository contains the Quarkus backend for the Japanese Learning application.

The backend uses a reactive architecture and must remain non-blocking.

Primary goals:

- Correctness
- Clean Code
- SOLID principles where appropriate
- Readability
- Maintainability
- Consistency with existing architecture
- Minimal and focused changes
- Efficient repository exploration

---

## Tech Stack

- Java
- Quarkus
- Hibernate Reactive Panache
- Mutiny
- MySQL
- Flyway
- Maven
- JUnit
- REST Assured where appropriate

Do not introduce new frameworks or libraries when the existing stack already solves the problem.

---

## Architecture

Follow the existing project structure and conventions.

Typical flow:

```text
Resource
  ↓
Service
  ↓
Repository
  ↓
Database
```

Responsibilities:

- Resource: HTTP request/response concerns
- Service: business logic and orchestration
- Repository: persistence and database access
- Database: schema and data integrity

Rules:

- Keep Resources thin.
- Keep business logic in Services.
- Keep persistence logic in Repositories.
- Do not introduce new architectural layers unless clearly required.
- Prefer existing project patterns over introducing new ones.
- Preserve separation of concerns.

---

## Clean Code and SOLID

All new or modified code must be:

- readable
- simple
- maintainable
- testable
- focused
- easy to understand

Apply SOLID pragmatically.

Rules:

- A class or method should have one clear responsibility.
- Avoid mixing validation, business logic, persistence, and response construction.
- Use clear and descriptive names.
- Keep methods focused and reasonably small.
- Prefer early returns when they reduce nesting.
- Avoid deeply nested conditionals.
- Avoid duplicated logic.
- Avoid magic values when an enum or constant is appropriate.
- Keep interfaces focused.
- Prefer straightforward code over clever code.
- Preserve separation of concerns.
- Avoid comments that merely repeat the code.
- Comment only to explain intent, constraints, or non-obvious decisions.
- Do not create abstractions for hypothetical future requirements.
- Do not over-engineer.
- Refactor only when it directly supports the requested task.

Code should be understandable by another developer without extensive explanation.

---

## Existing Code Is the Source of Truth

When implementation details are not explicitly specified:

1. Find the closest existing implementation.
2. Inspect one or two similar examples.
3. Follow existing naming, package, response, validation, exception, logging, and testing conventions.
4. Reuse existing components before creating new ones.

Before creating a new:

- DTO
- response type
- exception
- validator
- helper
- utility
- repository method
- abstraction

search for an existing equivalent first.

If this guide conflicts with current implementation, verify the relevant source code before making a broad change.

---

## Reactive Programming

The application is reactive.

Rules:

- Keep request flows non-blocking.
- Use `Uni`, `Multi`, and existing Mutiny patterns.
- Use Hibernate Reactive Panache.
- Do not introduce blocking Hibernate/JPA APIs.
- Do not call blocking operations from reactive request flows.
- Do not use `.await().indefinitely()` inside request flows.
- Do not replace reactive code with synchronous code for convenience.
- Preserve reactive return types where appropriate.
- Avoid unnecessary reactive complexity.

Before introducing a potentially blocking library or external call, inspect how the project already handles such operations.

---

## Dependency Injection

- Prefer constructor injection.
- Follow existing CDI conventions.
- Avoid field injection unless required by an existing framework pattern.
- Do not manually instantiate CDI-managed services.
- Keep dependencies explicit and minimal.

---

## REST API

Follow existing REST conventions.

When modifying or adding endpoints:

- Preserve API versioning style.
- Follow existing path conventions.
- Use the appropriate HTTP method.
- Reuse existing DTOs when appropriate.
- Do not expose persistence entities unless explicitly intended.
- Reuse the common API response structure.
- Preserve status code conventions.
- Keep Resources thin.
- Preserve backward compatibility unless the task requires an API change.

---

## API Response

Reuse the existing common response implementation.

Prefer existing components such as:

- `ApiResponse`
- `ErrorResponse`
- `ResponseMeta`
- `PaginationMeta`
- trace ID components
- correlation ID components

Rules:

- Do not introduce another response wrapper unless explicitly requested.
- Keep success and error responses consistent.
- Do not duplicate response-building logic when an existing helper already handles it.

---

## Exception Handling

Use centralized exception handling.

Rules:

- Avoid endpoint-level `try/catch` unless specifically needed.
- Prefer meaningful application/domain exceptions.
- Reuse existing exception mapping.
- Preserve error codes and response structures.
- Do not expose stack traces or internal implementation details.
- Do not catch exceptions only to rethrow them unchanged.
- Do not silently swallow errors.

Handle exceptions at the layer where they can be meaningfully interpreted.

---

## Validation

Follow existing validation conventions.

Rules:

- Validate at the appropriate application boundary.
- Reuse existing validation and error mechanisms.
- Do not duplicate validation across multiple layers.
- Use standard validation when sufficient.
- Add custom validation only when necessary.
- Keep validation messages consistent.
- Keep request-format validation separate from business-rule validation when appropriate.

---

## Database and Flyway

All schema and seed-data changes must use Flyway.

Rules:

- Never modify an already-applied migration unless explicitly instructed.
- Create a new migration for schema or seed changes.
- Inspect nearby migrations before adding a new one.
- Follow existing migration naming conventions.
- Preserve foreign keys, indexes, constraints, and naming conventions.
- Do not make unrelated database changes.
- Do not weaken database constraints merely to make application code pass.

Before adding a table, column, index, or constraint, inspect related existing migrations.

---

## Repository Layer

Follow existing Hibernate Reactive Panache patterns.

Rules:

- Keep persistence concerns in repositories.
- Do not place business logic in repositories.
- Preserve reactive return types.
- Prefer targeted queries.
- Avoid loading unnecessary data.
- Avoid N+1 query patterns where practical.
- Reuse existing query patterns.
- Keep repository methods focused.

Do not create duplicate repository methods without a clear reason.

---

## Service Layer

Services contain business logic and orchestration.

Rules:

- Keep business logic out of Resources.
- Keep persistence implementation details out of Services.
- Keep methods focused and readable.
- Avoid large methods with multiple responsibilities.
- Reuse existing helpers.
- Avoid unnecessary abstractions.
- Keep orchestration explicit and understandable.
- Preserve reactive flow.

---

## Logging

Follow existing logging conventions.

Rules:

- Do not log sensitive data.
- Prefer structured and useful logs.
- Avoid noisy logging.
- Avoid logging inside large loops unless necessary.
- Log failures at the appropriate layer.
- Reuse trace and correlation IDs.
- Avoid duplicate logging of the same failure across layers.
- Use appropriate log levels.
- Do not log obvious control flow unnecessarily.

---

## Testing

Use the existing test structure and conventions.

Rules:

- Add or update tests when behavior changes.
- Prefer focused tests.
- Test important success and failure paths.
- Reuse existing test patterns.
- Keep test data aligned with current migrations and behavior.
- Do not modify unrelated tests merely to make the build pass.
- Do not blindly change expected values.

When a test fails:

1. Determine whether the implementation or test is incorrect.
2. Understand the intended behavior.
3. Fix the root cause.
4. Explain meaningful expectation changes.

During implementation:

- Run the smallest relevant test set first.
- Run broader tests only for wider or cross-cutting changes.
- Do not run the full suite after every small edit.

Before finalizing:

- Run relevant tests.
- Run a broader build or test suite only when appropriate or explicitly requested.
- Report pre-existing failures separately.

---

## Git Safety

Before editing:

```bash
git branch --show-current
git status --short
```

Preserve unrelated user changes.

Do not:

- commit
- push
- pull
- switch branches
- rebase
- reset
- clean
- stash
- discard changes

unless explicitly requested.

Never overwrite unrelated uncommitted work.

---

## Scope Control

Keep every task narrowly scoped.

Rules:

- Implement only what is required.
- Do not refactor unrelated code.
- Do not rename unrelated classes, packages, methods, variables, or database objects.
- Do not change public APIs unless required.
- Do not perform repository-wide cleanup.
- Do not upgrade dependencies unless required.
- Do not reformat unrelated files.
- Prefer the smallest correct change.

If an unrelated issue is discovered, report it instead of automatically fixing it.

---

## Repository Exploration

Use progressive discovery.

Do not scan the entire repository by default.

Preferred workflow:

1. Read this guide.
2. Understand the task.
3. Inspect files explicitly mentioned in the task.
4. Inspect the closest related implementation.
5. Inspect one or two similar implementations if needed.
6. Use targeted searches for missing information.
7. Expand scope only when current context is insufficient.

Prefer searches by:

- class name
- method name
- endpoint
- DTO
- exception
- table
- migration
- error code

Examples:

```bash
rg "VocabularyService"
rg "ApiResponse"
rg "/api/v1/vocabularies"
```

Avoid:

- reading every Resource
- reading every Service
- reading every Repository
- reading every migration
- scanning generated files
- scanning build output
- repeatedly reading unchanged files
- preloading large parts of the repository "for context"

---

## Context and Command Efficiency

Minimize unnecessary context and command output.

Rules:

- Read only relevant file sections.
- Prefer targeted `rg` searches.
- Do not repeatedly inspect unchanged files.
- Do not load unrelated documentation.
- Do not inspect generated files or build output unless debugging requires it.
- Do not repeat commands when the result is already known.
- Do not run expensive commands without a reason.
- Use existing code as documentation whenever possible.

Prefer:

```bash
git status --short
git branch --show-current
git diff --stat
git diff
rg "<search-term>"
```

For Maven, prefer targeted tests during implementation.

Example:

```bash
./mvnw test -Dtest=VocabularyServiceTest
```

Avoid repeated:

- full repository scans
- full builds
- full test suites
- commands producing large irrelevant output

---

## Before Coding

Before modifying files:

1. Understand the requested behavior.
2. Inspect Git state.
3. Identify the smallest relevant file set.
4. Find the closest existing implementation.
5. Check existing conventions.
6. Determine the minimal intended change.

If ambiguity could significantly affect:

- architecture
- public API
- database schema
- compatibility
- security
- business behavior

ask before making a broad change.

For small implementation details, prefer existing project conventions instead of asking unnecessary questions.

---

## During Coding

While implementing:

- Keep changes minimal.
- Write clean, readable, maintainable code.
- Apply SOLID where appropriate.
- Follow existing project style.
- Preserve reactive behavior.
- Reuse existing patterns.
- Avoid duplication.
- Keep responsibilities separated.
- Use meaningful names.
- Avoid unnecessary dependencies.
- Avoid unnecessary abstractions.
- Avoid speculative functionality.
- Do not modify generated files.
- Do not perform unrelated cleanup.

---

## After Coding

After implementation:

1. Run relevant tests.
2. Compile/build when appropriate.
3. Inspect the final diff.
4. Confirm no unrelated files changed.
5. Remove debug or temporary code.
6. Verify code remains clean and understandable.
7. Report the actual result.

Recommended:

```bash
git diff --stat
git diff
```

Do not commit or push unless explicitly requested.

---

## Review Tasks

When asked only to review:

- Treat the task as read-only.
- Do not modify files.
- Review the current diff first when applicable.
- Inspect surrounding code only when needed.
- Avoid expensive commands unless necessary.

Focus on:

- correctness
- regressions
- Clean Code
- SOLID
- readability
- maintainability
- architecture consistency
- reactive correctness
- API compatibility
- validation
- exception handling
- security
- migration safety
- test coverage

Prioritize concrete problems over subjective style preferences.

---

## Response Style

Keep final responses concise.

For implementation tasks, report:

- what changed
- files changed
- tests/build actually executed
- result
- important risks or unresolved issues

Do not:

- repeat large code blocks already written to files
- narrate repository exploration
- list every command unless relevant
- explain obvious implementation details
- generate extensive documentation unless requested
- claim an unrun test or build passed

Example:

```text
Implemented vocabulary lookup by ID.

Changed:
- VocabularyResource.java
- VocabularyService.java
- VocabularyRepository.java
- VocabularyResourceTest.java

Verification:
- VocabularyResourceTest: PASS
- Maven compile: PASS

No unrelated files were changed.
```

---

## Definition of Done

A task is complete when:

- requested behavior is implemented
- implementation is correct
- code is clean and readable
- SOLID is respected where appropriate
- code is easy to maintain
- existing architecture is preserved
- reactive behavior is preserved
- relevant tests pass
- the project compiles when appropriate
- no unrelated code was modified
- the final diff was reviewed
- important risks or limitations were reported

Do not commit or push unless explicitly requested.