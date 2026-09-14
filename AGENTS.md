# AGENTS.md

## Project Overview

This repository contains the Quarkus backend for the Japanese Learning application.

The project uses a reactive architecture and should remain non-blocking.

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

## Architecture

Follow the existing project structure and conventions.

Typical flow:

Resource
-> Service
-> Repository
-> Database

Do not introduce new architectural layers unless the task clearly requires them.

Prefer consistency with existing code over introducing a new pattern.

## Reactive Programming

This project uses reactive APIs.

Rules:

- Keep database operations non-blocking.
- Use `Uni` and existing Mutiny patterns where appropriate.
- Do not introduce blocking Hibernate/JPA APIs.
- Do not call blocking operations from reactive request flows.
- Follow existing Hibernate Reactive Panache patterns.
- Do not replace reactive code with synchronous code for convenience.

## Dependency Injection

- Prefer constructor injection.
- Follow existing CDI conventions.
- Avoid field injection unless the surrounding code already requires it.

## REST API

Follow the existing REST resource conventions.

When implementing or modifying endpoints:

- Preserve the existing API versioning style.
- Follow existing path and HTTP method conventions.
- Reuse existing DTOs when appropriate.
- Do not expose persistence entities directly unless the current design explicitly does so.
- Reuse the project's common API response structure.
- Preserve existing status code conventions.

## API Response

Reuse the existing common response implementation.

Do not introduce another response wrapper unless explicitly requested.

When working with API responses, inspect and reuse existing components such as:

- `ApiResponse`
- `ErrorResponse`
- `ResponseMeta`
- `PaginationMeta`
- request trace / correlation ID components

Keep success and error responses consistent across endpoints.

## Exception Handling

Use the project's centralized exception handling.

Rules:

- Do not add endpoint-level try/catch blocks unless there is a specific reason.
- Prefer domain/application exceptions over generic runtime exceptions.
- Map exceptions through the existing exception handling mechanism.
- Preserve error codes and response structure conventions.
- Do not expose internal stack traces or implementation details through APIs.

## Validation

Follow existing validation conventions.

Rules:

- Keep request validation close to the appropriate application boundary.
- Reuse existing validation/error response mechanisms.
- Do not duplicate the same validation logic across Resource, Service, and Repository layers.
- Add custom validation only when standard validation is insufficient.
- Keep validation messages consistent with existing project conventions.

## Database

All schema changes must use Flyway.

Rules:

- Never modify an already-applied migration unless explicitly instructed.
- Create a new migration for schema or seed-data changes.
- Follow the existing Flyway naming convention.
- Inspect existing migrations before adding a new one.
- Preserve foreign keys, indexes, constraints, and naming conventions.
- Do not make unrelated database changes.

## Repository Layer

- Follow existing Hibernate Reactive Panache patterns.
- Keep persistence concerns inside repository classes.
- Avoid embedding business logic in repositories.
- Use targeted queries instead of loading unnecessary data.
- Preserve reactive return types.

## Service Layer

- Keep business logic in the service layer.
- Keep methods focused and readable.
- Avoid unnecessary abstractions.
- Reuse existing helper methods when appropriate.
- Do not move persistence logic into Resources.

## Logging

Follow the existing application logging conventions.

Rules:

- Do not log sensitive data.
- Prefer structured, useful logs.
- Avoid noisy logs inside loops unless necessary.
- Log failures at the appropriate layer.
- Reuse existing trace/correlation IDs where available.
- Do not add duplicate logging across multiple layers for the same event.

## Testing

Use the existing test structure and conventions.

Rules:

- Add or update tests when behavior changes.
- Prefer focused tests for the changed behavior.
- Run the smallest relevant test set during implementation.
- Run the full test suite only when necessary or before final verification.
- Do not modify unrelated tests merely to make the build pass.
- Report pre-existing failing tests separately.
- Keep test data aligned with the current application data and migrations.

When fixing a failing test:
1. Determine whether the test is stale or the implementation is incorrect.
2. Do not blindly change the expected value.
3. Explain the reason for the change.

## Git Safety

Before making changes:

- Inspect the current branch.
- Inspect existing uncommitted changes.
- Preserve user changes.

Do not:

- commit
- push
- pull
- switch branches
- rebase
- reset
- clean
- stash
- discard user changes

unless explicitly requested.

Never overwrite unrelated uncommitted work.

## Scope Control

Keep every task narrowly scoped.

Rules:

- Do not refactor unrelated code.
- Do not rename unrelated classes, packages, methods, or variables.
- Do not change public APIs unless required.
- Do not perform large cleanup work unless explicitly requested.
- Prefer the smallest change that correctly solves the task.

## Context Efficiency

Do not scan the entire repository by default.

Preferred workflow:

1. Start with files explicitly named in the task.
2. Inspect one or two nearby implementations with similar behavior.
3. Use targeted searches such as `rg`.
4. Expand the search only when necessary.
5. Avoid repeatedly reading files that have not changed.
6. Do not inspect generated files or build output.
7. Do not run the full test suite after every small edit.

Avoid broad repository-wide searches unless needed to understand architecture or cross-cutting behavior.

## Before Coding

Before modifying files:

1. Understand the task.
2. Inspect the current Git state.
3. Identify the smallest relevant set of files.
4. Find the closest existing implementation to use as a reference.
5. Briefly determine the intended change.

If the task is ambiguous or could significantly affect architecture, ask before making broad changes.

## During Coding

- Keep the change set minimal.
- Follow existing code style.
- Prefer existing patterns over inventing new ones.
- Do not add unnecessary dependencies.
- Do not add abstractions for hypothetical future requirements.
- Do not modify generated files.

## After Coding

After implementation:

1. Run relevant tests.
2. Compile/build when appropriate.
3. Inspect `git diff`.
4. Check that no unrelated files were changed.
5. Summarize:
    - files changed
    - behavior implemented
    - tests executed
    - build/test result
    - known risks or unresolved issues

Do not commit or push.

## Review Tasks

When asked only to review:

- Treat the task as read-only.
- Do not modify files.
- Do not run expensive commands unless needed.
- Prefer reviewing the current branch diff first.
- Inspect surrounding code only where required.

For branch reviews, focus on:

- correctness
- regressions
- architecture consistency
- reactive correctness
- API compatibility
- validation
- exception handling
- security risks
- database migration safety
- test coverage

## Command Efficiency

Prefer targeted commands.

Examples:

- `git status --short`
- `git branch --show-current`
- `git diff --stat`
- `git diff`
- targeted `rg` searches
- specific Maven tests

Avoid unnecessarily expensive commands or repeated full repository scans.

## Definition of Done

A task is complete when:

- requested behavior is implemented
- existing architecture is respected
- relevant tests pass
- no unrelated code was modified
- Git diff is reviewed
- important risks or limitations are reported