# Kanji Quiz Player API (Phases 4.1-4.4)

Configuration and creation require the case-sensitive `User` or `Admin` role and use the existing
API response/error envelope. Session reads are described in the Phase 4.2 section below.

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

## Session reads (Phase 4.2)

`GET /api/v1/kanji-quiz/sessions/{id}` returns the common HTTP 200 envelope with:

```json
{"sessionId":42,"status":"IN_PROGRESS","questionCount":10,"answeredCount":3,"score":2}
```

Score is the number of correct submitted answers, read from immutable selected options.
Answered count out of question count is session progress. No user-progress records or
grading state are written. Both totals are zero before any answers exist.

`GET /api/v1/kanji-quiz/sessions/{id}/next` returns:

```json
{"sessionId":42,"status":"IN_PROGRESS","question":{"sessionQuestionId":101,
 "questionNumber":0,"sentenceReading":"がっこう","targetStart":0,"targetLength":4,
 "options":[{"id":401,"text":"学校"},{"id":402,"text":"学交"},
            {"id":403,"text":"校学"},{"id":404,"text":"学高"}]}}
```

Both endpoints require User or Admin and the same usable, exact JWT subject as creation.
An Admin cannot read another subject's session. Missing and foreign IDs return the
same 404 `QUIZ_SESSION_NOT_FOUND`. Nonpositive IDs return 400; malformed or overflowing
path IDs follow the existing REST conversion behavior (404). Authentication/role
failures use the existing 401/403 envelopes.

The next question is the first snapshot without an answer, ordered by zero-based
questionNumber. Options retain ascending snapshot ID order from the original shuffle.
Target offsets and lengths are Unicode code points. No bank IDs, correctness flags,
answer keys, target reading, or explanations appear. Reads remain valid after bank/source
edits or deletion and never reshuffle, submit, advance, score, finish, or update progress.

Completed or abandoned sessions are still readable; their next question is explicitly
null. Exhausted or empty snapshot sets also return `question: null`, with the stored
status unchanged. Each read uses a reactive transaction for a consistent database view.
Answer submission and explicit completion are described below.

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

## Verification and handoff

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
OpenAPI tests fetch the live merged document and verify the six-operation scope, security,
request bounds, examples and response fields.

Phase 4.2 loads sessions through the subject-scoped repository and serves only safe
snapshot DTOs. Focused `QuizSessionReadMysqlTest` coverage includes IDOR (including
Admin, subject case and whitespace), persisted order, answered holes, score totals,
repeated reads without mutation, terminal/empty sessions, and history after bank edits
and deletion. Merged OpenAPI checks verify both read operations, response fields,
nullable next questions, examples and security.

History and user-progress reporting remain deferred to Phase 5.

## Answer submission (Phase 4.3)

`POST /api/v1/kanji-quiz/sessions/{id}/answers` requires JSON snapshot IDs:

```json
{"sessionQuestionId":101,"selectedOptionId":402}
```

Only the next unanswered snapshot in persisted question order is accepted. The selected
option must belong to that snapshot. HTTP 200 returns feedback in the common envelope:

```json
{"sessionQuestionId":101,"correct":false,"correctOptionId":401,
 "explanationVi":null,"explanationEn":"Explanation","score":2,
 "answeredCount":4,"remainingCount":6}
```

Correctness and nullable explanations come exclusively from immutable snapshots.
Score and answered count are derived from persisted answers, not client values or
mutable bank data. The session row is locked before reads or writes; answer insert
and returned counts share one reactive transaction. Failures roll back. Concurrent
submissions cannot double score. Last answer leaves status `IN_PROGRESS` until finish.

- 400 `BAD_REQUEST`: malformed JSON or missing/nonpositive IDs.
  `QUIZ_ANSWER_INVALID`: selected option is not part of the current snapshot.
- 409 `QUIZ_ANSWER_CONFLICT`: duplicate, out-of-order, exhausted, terminal session,
  or locking conflict. Rejection does not reveal the answer key.
- 415: application/json required.
- Ownership/security and sanitized server errors follow the contracts below.

## Session completion (Phase 4.4)

`POST /api/v1/kanji-quiz/sessions/{id}/finish` has no request body. Only an
`IN_PROGRESS` session with all `questionCount` questions answered may complete.
It returns HTTP 200 in the common envelope:

```json
{"sessionId":42,"status":"COMPLETED","questionCount":10,
 "correctCount":7,"incorrectCount":3,"score":7,
 "completedAt":"2026-10-09T10:15:30.123456"}
```

`questionCount` is the total; `correctCount + incorrectCount` equals that total.
`score` equals `correctCount`. `completedAt` is the persisted UTC LocalDateTime,
serialized without an offset at microsecond precision. The existing `COMPLETED`
status is retained throughout the schema, Java enum, API and documentation.
No migration or status rename is needed.

Finish locks the same owned session row as answer submission before checking status
or counting answers. It updates status and completion time atomically without rewriting
questions, options, answers or scores. Concurrent finishes yield one success and one 409.
A finish racing the last answer either sees the committed answer and completes, or
returns premature 409; callers must retry explicitly after the answer succeeds.
There is no automatic retry. Duplicate finish never replaces the original timestamp.
Answers after completion return 409. GET session still returns the final score and
`COMPLETED`; GET next returns `question: null`. Neither GET exposes answer keys.
No `quiz_user_progress` records or history/progress endpoints are added.

Shared player write errors:

- 400 `BAD_REQUEST`: nonpositive session ID.
- 401: missing/invalid JWT or blank/overlong subject; 403: wrong case-sensitive role.
- 404 `QUIZ_SESSION_NOT_FOUND`: identical for missing and foreign sessions, including
  Admin. Ownership compares the exact verified JWT subject. Malformed/overflowing
  path IDs follow existing REST conversion behavior and return 404 `NOT_FOUND`.
- 409 `QUIZ_FINISH_CONFLICT`: premature finish, already completed/abandoned session,
  or locking failure. No completion is committed.
- 500: unexpected failure; transaction rolls back and internal details stay sanitized.

## Phase 4 verification

`QuizFinishMysqlTest` checks final totals/time, unchanged snapshot and answer rows,
read secrecy after completion, premature/duplicate/abandoned rejection, exact ownership,
rollback after a database failure, two simultaneous finishes, and finish/answer races
observed at the actual MySQL row lock. `QuizGameMysqlTest` also exercises config ->
create -> retrieve -> next -> answer -> finish with both EXAMPLE and CUSTOM snapshots
and source invalidation during a game. Existing creation, retrieval, answer, foundation,
Admin and import tests cover randomization, answer secrecy and immutable data after edits.
Merged OpenAPI tests verify all six player operations, including bodyless finish.

Run focused tests first, then one `mvn verify` with real isolated MySQL after changes
stabilize. Apply and validate V1-V7 through external Flyway; production startup does
not run migrations. Test-only failure injection uses temporary triggers removed in
`finally`; no production triggers are introduced.
