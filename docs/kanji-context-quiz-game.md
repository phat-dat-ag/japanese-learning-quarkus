# Kanji Quiz game configuration and creation (Phase 4.1)

Both endpoints require the case-sensitive `User` or `Admin` role and use the existing
API response/error envelope. No other Player operations are introduced.

## Contracts

`GET /api/v1/kanji-quiz/config` returns HTTP 200 with:

```json
{"totalQuestions":25,"maxQuestionCount":100,
 "levels":[{"id":1,"code":"N5","name":"JLPT N5","questionCount":20}],
 "lessons":[{"id":1,"levelId":1,"lessonNumber":1,"title":"Lesson 1","questionCount":10}]}
```

Only positive-count levels and lessons appear. The total includes unclassified
questions. Each count uses distinct question IDs; classifications may overlap, so
summing their counts does not yield the total. Explicit level tags and levels implied
by assigned lessons both count. Levels follow master display order, lessons follow
level ID then lesson display order. Empty banks return zero and empty lists. Counts
are a transactional database snapshot, not a reservation or availability guarantee.

`POST /api/v1/kanji-quiz/sessions` accepts JSON:

```json
{"levelId":1,"lessonId":1,"questionCount":10}
```

Both filters are optional/nullable. IDs must be positive and reference existing rows;
when both are supplied the lesson must belong to the selected level. Filters intersect.
A level matches explicit tags or an assigned lesson at that level; a lesson matches
an explicit question assignment. Unclassified questions are eligible only without
filters. `questionCount` is required and ranges from 1 to 100. There is no automatic
count reduction. Unknown fields follow existing Jackson policy (ignored), but no
client identity field is ever used.

HTTP 200 returns committed session metadata only:

```json
{"sessionId":42,"status":"IN_PROGRESS","questionCount":10,"createdAt":"2026-10-08T00:00:00"}
```

Timestamps follow existing Quiz UTC LocalDateTime serialization. Owner is the exact,
case-sensitive verified JWT `sub`, nonblank and at most 255 Unicode code points.
Neither response exposes correctness, options, explanations or question content.
Session creation is not idempotent: repeating a successful POST creates a new session.

- 400 `BAD_REQUEST`: JSON/Bean Validation; `QUIZ_GAME_INVALID`: nonexistent filter
  references or level/lesson mismatch, with field details.
- 401/403: existing authentication/role errors; unusable session owner subjects are 401.
- 409 `QUIZ_INSUFFICIENT_QUESTIONS`: message includes requested and available counts.
  `QUIZ_GAME_CONFLICT`: concurrent bank changes or persistence/locking conflicts.
  No partial session survives either error; caller may explicitly retry.
- 415: session creation requires application/json.
- 500: unexpected infrastructure failure; existing sanitized error handling applies.

The hybrid Java/static contract and examples are verified through merged `/q/openapi`.

## Selection, snapshots and concurrency

The existing Phase 1 eligibility predicate is shared by configuration, candidate paging
and randomized selection: PUBLISHED CUSTOM or EXAMPLE with a live matching source
fingerprint. Supported Admin writes validate publication and demote edited content;
direct SQL corruption remains outside supported application write paths.

Random selection uses MySQL `ORDER BY RAND()` with a maximum of 100 selected rows,
without replacement. This scans/sorts the matching bank; a larger production bank may
need a measured sampling optimization later. No full entity bank is loaded into Java.
Each snapshot option set is independently shuffled before sequential identity inserts.
Reading snapshot options by ascending snapshot-option ID preserves that session's
shuffle without a schema/display-order column or a relationship to bank option IDs.

One `@WithTransaction` spans reference validation, selection, locking, revalidation and
all snapshot writes. Selected reference filters are read with shared locks. The reused
snapshot service locks questions in ID order then sources in ID order, validates live
publication/fingerprints/targets/options, and checks classifications with locking current
reads. Those current reads avoid stale classifications from an earlier MySQL repeatable-
read candidate query. Concurrent edits either precede the captured snapshot or cause
409; no invalid or partially persisted game is returned. There is no automatic retry
of a failed transaction. Concurrent sessions may select the same bank question safely.

Phase 1 immutable snapshot entities retain sentence, target, explanations, source/version
and correct option independently of later bank/source edits or deletion. No migrations,
triggers, new dependencies or changes to Vocabulary APIs are needed.

## Verification and Phase 4.2 handoff

`QuizGameMysqlTest` covers both sources, counts, explicit/implied/intersecting filters,
randomized subsets/order/options, JWT ownership, validation, source invalidation,
concurrent creations, and rollback after an injected snapshot-option constraint failure.
Controlled lock-wait races cover classification, publication and example-reading changes.
They observe Performance Schema wait edges for the exact question row and blocking
connection before committing the mutation. Rapid polling of cached `INNODB_TRX` can
miss an active wait; it is not a reliable synchronization barrier. Failed races explicitly
roll back the mutation and drain the pending request so later cases are not contaminated.

Run the regression suite against an isolated MySQL 8 database named
`vocabulary_import_test`, initialized with V1-V7 using external Flyway. Performance
Schema must be enabled (the standard MySQL container default); the existing test profile
uses the disposable database's root account to inspect lock waits.

```powershell
.\mvnw.cmd -B -ntp verify '-Dquarkus.http.test-port=0' '-Dvocabulary.mysql.tests=true' `
  '-Dvocabulary.mysql.url=mysql://127.0.0.1:13306/vocabulary_import_test'
```

Port 13306 belongs to the disposable test container, not the development database.
The test profile's default password is `vocabulary-test`; override it with
`-Dvocabulary.mysql.password` when needed.

`QuizFoundationMysqlTest` continues to cover immutable history and snapshot constraints.
OpenAPI tests fetch the live merged document and verify the two-operation scope, security,
request bounds, examples and response fields.

Phase 4.2 must load sessions through the existing subject-scoped repository, serve only
safe snapshot DTOs, and order questions by questionNumber and options by snapshot ID.
It must never read current bank content to grade or expose internal correctness to players.
Retrieval, next-question, answers, scoring, finish, history and progress remain deferred;
full end-to-end game testing belongs to Phase 4.4. No foundation blocker is currently known.
