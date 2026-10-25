# Flashcard learning

## Approved implementation design

This feature stays in the existing Quarkus application and consumes vocabulary and
lesson data. Ownership is the exact verified JWT subject, never a request user ID.
Existing flashcard list/detail contracts remain unchanged unless `includeLearning=true`.

### Scheduling versus practice

`NEW_AND_DUE` and `DUE` sessions are SRS sessions. Eligibility is checked both when
creating a session and when accepting a review. `ALL` sessions are non-scheduling
practice: ratings are recorded and items completed, but progress, repetitions,
lapses, mastery and due timestamps are never changed. A practice-only vocabulary
remains NEW but counts as studied. Statistics expose separate scheduled/practice
attempt totals so this distinction is visible.

One accepted review per session item is supported. Exact retries return the stored
result; changed requests conflict. Future relearning rounds should create new item
occurrences (with round/occurrence identity), retaining item-based idempotency and
history. They will require a migration replacing session/vocabulary uniqueness;
the MVP does not silently requeue AGAIN cards.

### Normal empty state

Session creation returns HTTP 200 with `outcome=NO_ELIGIBLE_CARDS`, `eligibleCount=0`
and no session when selection is empty. It writes nothing. Due and bookmark lists
return normal empty paginated results. Invalid lesson references remain errors.

### Persistence and concurrency

V9 adds progress, bookmarks, sessions, items and immutable review history. Subjects
use MySQL's case-sensitive, NO PAD utf8mb4_0900_bin collation; no user-service FK.
Session item membership/order is frozen; vocabulary content remains live.
Reviews lock the owned session and use a progress version precondition plus
optimistic locking across sessions. Unique progress keys protect first-insert races.
Transactions flush before returning, and failures are translated outside the
transaction so rollback/commit failures cannot become false successes. Conflicts
require client refresh; failed reactive transactions are never reused or retried.

### Statistics

The denominator is distinct current catalogue vocabulary in the selected scope:
overall, explicit vocabulary level membership, or lesson membership. Filters
intersect. Overlapping scopes must not be summed. `studiedPercentage` uses distinct
vocabulary with at least one accepted review (including practice);
`masteryPercentage` uses current MASTERED progress. Empty scopes yield 0.00.
Attempts include history in current scope, not historical classification snapshots.
AGAIN history identifies frequently forgotten vocabulary; bookmarks remain
independent and no difficult-word table is introduced.

### Simplified SRS v2

This is a heuristic four-rating algorithm, not exact SM-2. Initial ease is 2.5,
bounded to 1.3–3.0. AGAIN schedules 10 minutes, resets consecutive remembered
ratings, lowers ease by 0.20, and returns to LEARNING. It increments lapses only
after a prior scheduled review. HARD lowers ease by 0.15 and schedules 1 day
initially, then max(previous days + 1, previous days * 1.2). GOOD uses 1 day,
then 3 days, then previous days * ease. EASY raises ease by 0.15 and uses at least
4 days or previous days * ease * 1.3. Newly calculated successful intervals round up and cap at
365 days. Successful reviews never shorten the previous interval: EASY then GOOD retains at least four days. Lowering the configured cap also preserves an existing longer interval until forgetting; AGAIN resets it. History identifies this behavior as simplified-srs-v2. Two consecutive remembered ratings enter REVIEW; five and an interval
of at least 21 days enter MASTERED. Remembered ratings preserve mastery; AGAIN
clears the current mastered timestamp. MASTERED remains scheduled.

Configuration bounds the AGAIN delay, maximum interval and mastery thresholds.
Time is captured once per operation, stored at UTC microsecond precision and
returned with Z. Algorithm inputs/results and version are retained in history.

### Tradeoffs and verification

Sessions are bounded to 100 items; multiple active sessions are allowed. Concurrent
sessions may become stale and return 409 instead of applying an early extra SRS
review. Practice offers an explicit way to revisit those cards. No full content
snapshots, historical classification snapshots, automatic session expiry, account
deletion integration or relearning rounds are included. Historical references
restrict deletion; existing APIs do not expose catalogue deletion.

Each implementation stage is compiled/tested before the next. Offline tests cover
calculations, contracts and ownership; opt-in MySQL tests must establish actual
Hibernate Reactive transaction, uniqueness, locking and rollback behavior on a
disposable externally migrated `vocabulary_import_test` database. A skipped MySQL
test is not evidence of verified concurrency.


## API contract

All routes below start with `/api/v1/flashcards`. Success uses the existing
`ApiResponse` envelope and HTTP 200. Personal responses include
`Cache-Control: private, no-store`. Missing/invalid tokens return 401, insufficient
roles 403, foreign/missing sessions the same 404, validation errors 400, and
concurrent/stale/conflicting writes 409 `FLASHCARD_CONFLICT`.

| Method | Route | Purpose |
|---|---|---|
| GET | `?level=N5&lesson=1&includeLearning=true` | Existing list plus per-card progress/bookmark state |
| GET | `/{id}?includeLearning=true` | Existing detail plus progress/bookmark state |
| POST | `/sessions` | Create bounded lesson session or return normal empty outcome |
| GET | `/sessions?lessonId=12&status=IN_PROGRESS` | Paginated owned session summaries, newest first |
| GET | `/sessions/{id}` | Resume persisted order and next unfinished position |
| PUT | `/sessions/{id}/items/{itemId}/review` | Accept/replay one rating |
| POST | `/sessions/{id}/finish` | Confirm already-completed session; no body |
| POST | `/sessions/{id}/abandon` | Abandon an incomplete owned session; no body; idempotent |
| GET | `/reviews/due` | Due cards ordered by nextReviewAt, then vocabulary ID |
| GET | `/reviews/forgotten` | Cards with AGAIN history, ranked by AGAIN count descending then ID |
| PUT | `/{vocabularyId}/bookmark` | Add bookmark; no body |
| DELETE | `/{vocabularyId}/bookmark` | Remove bookmark, absent is successful; no body |
| GET | `/bookmarks` | Bookmarks ordered by creation descending then vocabulary ID descending |
| GET | `/statistics` | Overall or scoped personal counts and percentages |

New card lists and statistics accept optional `level` and `lessonId`. These are
intersected; a selected lesson must belong to the selected level. Omitted filters
mean overall scope. Existing `lesson` remains a lesson **number**, and the existing
list still defaults to N5. New lists use `items`; the existing list retains
`flashcardItems`. Pagination is zero-based, defaults to size 20, maximum 100.
Level/lesson overlap is deduplicated through membership EXISTS predicates.

### Start and resume

```json
{"lessonId":12,"mode":"NEW_AND_DUE","cardLimit":20,"shuffle":false}
```

Modes default to NEW_AND_DUE and cardLimit to 20. The server selects at most 100
cards, prioritizing due cards before unseen cards in NEW_AND_DUE. For ALL with
shuffle=true, MySQL samples across the entire lesson before applying the limit.
Only at most 100 IDs enter application memory. Database random selection still
scans/sorts the lesson; it is not a constant-time operation. Without shuffle,
ALL selects never-practiced vocabulary first, then least-recently-practiced
vocabulary, with lesson display order and vocabulary ID as tie breakers. Completing
practice sessions therefore advances coverage without modifying SRS. Merely
creating or abandoning a session does not mark its remaining cards practiced.
Scheduled modes still shuffle only their selected due/new subset. Requests do not create progress. A repeated successful
creation request creates another session; clients should retain the returned ID
or retrieve their session list after uncertain delivery.

`data.outcome` is CREATED with an embedded session, or NO_ELIGIBLE_CARDS with
`eligibleCount: 0` and omitted `session`. For CREATED, eligibleCount is the selected
count, bounded by cardLimit, not the count of all eligible cards in the lesson.
An empty due list is `items: [], totalElements: 0, totalPages: 0`.

Session detail contains ordered item IDs, vocabulary IDs, positions, completion
timestamps and current progress versions. `currentPosition` is the number of
completed items and identifies the next unfinished position. It equals itemCount
when complete. Reads never advance, reshuffle or rate cards. Session lists omit
items; fetch detail to resume. The final rating completes the session atomically.

### Rate or practice

```json
{"rating":"GOOD","expectedProgressVersion":0}
```

Zero means no SRS progress. The first scheduled review returns version 1; subsequent
updates increment it. Use the latest version from session detail or an enriched
card. SRS requests reject future-due progress even if the session was created
earlier. Another session can therefore make an item stale; resume after it is due
or abandon it and start another eligible session. Future-due rejection uses HTTP
409 FLASHCARD_NOT_DUE, taking precedence over a stale version; ordinary version
conflicts remain FLASHCARD_CONFLICT. Angular should not automatically retry a
NOT_DUE response.

POST /sessions/{id}/abandon has no body and returns the session with status
ABANDONED and abandonedAt in UTC. It locks the owned session in a transaction,
preserving all accepted reviews, progress, completed counts and item order.
Repeated abandonment returns the same state; completed sessions remain COMPLETED.
New reviews on abandoned sessions conflict, but exact retries of already accepted
reviews still return their original result. List filters accept ABANDONED.
Abandonment and reviews serialize on the same session lock. Missing and foreign
sessions return the same 404, including for Admin callers. There is no skip operation.

The review response contains review ID, item ID, vocabulary ID, rating, scheduling
flag, resulting progress and reviewedAt. Retry with the exact rating and expected
version to recover the original result, including after session completion. A
changed retry is a conflict. Current session state is retrieved separately; an
old review response is intentionally an immutable historical result.

ALL ignores the progress precondition for scheduling purposes because it writes
no progress. It still stores the supplied version as part of request identity.
Practice may show the observed SRS state but never changes it. Practice AGAIN
contributes to forgotten-attempt statistics/ranking, not SRS lapse counters.

### Counts and percentages

Status counts sum to the distinct catalogue denominator. Due is an overlapping
subset that includes MASTERED. Studied is independent of status: practice-only
vocabulary is studied but still NEW for SRS. Attempt counts include active as well
as completed sessions and count accepted reviews only, never retries. HARD, GOOD
and EASY are remembered self-ratings; AGAIN is forgotten. These are self-reported
recall statistics, not objectively graded answers.

For two vocabulary items, one practiced and neither mastered, studiedPercentage
is 50.00 and masteryPercentage 0.00. Both percentages are zero for empty scopes.
Classification changes affect scoped totals because statistics use current
catalogue membership. Overall totals always count each vocabulary once.

## Configuration

No dependencies or authentication settings are added. Optional properties:

| Property | Default | Valid range |
|---|---|---|
| `flashcard.srs.again-seconds` | 600 | 1–86399 |
| `flashcard.srs.max-days` | 365 | 4–36500 |
| `flashcard.srs.mastery-repetitions` | 5 | At least 2 |
| `flashcard.srs.mastery-days` | 21 | 1 through max-days |

Invalid combinations fail bean initialization. Changes affect future reviews;
existing schedules remain unchanged. History stores before/after state and
algorithm version, so accepted results remain inspectable after configuration
changes. Repetitions are consecutive remembered scheduled reviews; lapses exclude
an initial AGAIN. masteredAt records the current mastery period.

## Migration and tests

Apply migrations through V11 with the existing external Flyway runner
before deploying this application version. Run Flyway validation against V1–V11. V10 adds abandonment without changing existing sessions; V11 adds session-list indexes.
No vocabulary, lesson or quiz backfill is required, and no user-service FK is added.
Database UTC conventions must be preserved; timestamps are explicitly written by
the application. Roll back application deployment without deleting learning data;
schema rollback is not an automatic destructive down-migration.

Focused offline tests:

```powershell
.\mvnw.cmd test "-Dtest=SpacedRepetitionServiceTest,VocabularyProgressServiceTest,FlashcardLearningApiTest,FlashcardResourceTest,FlashcardServiceTest,FlashcardListServiceTest,OpenApiResourceContractTest" "-Dquarkus.http.test-port=0"
```

Actual stack tests require a disposable MySQL 8 database named
`vocabulary_import_test`, externally migrated through V11:

```powershell
.\mvnw.cmd test "-Dtest=FlashcardLearningMysqlTest" "-Dquarkus.http.test-port=0" `
  "-Dvocabulary.mysql.tests=true" `
  "-Dvocabulary.mysql.url=mysql://127.0.0.1:3306/vocabulary_import_test" `
  "-Dvocabulary.mysql.password=<test-password>"
```

These tests mutate the disposable database and include temporary failure-injection
triggers. They verify rollback after a history write fails, optimistic locking,
first-review races with both transactions held after reading absent progress, duplicate submissions observed waiting on a held MySQL row lock, UTC due boundaries, bookmark races,
ownership, practice isolation, aggregation and HTTP contracts. Do not point them
at development or production data.

In the inspected Quarkus/JUnit setup, an unfiltered test discovery can instantiate
VocabularyMysqlTestProfile before evaluating the opt-in annotation and reject a
missing database URL. The focused offline command above avoids those classes.
Full verification was therefore run with disposable MySQL connection properties
and the opt-in enabled; this does not change production datasource configuration.

### Session-list query plans

On disposable MySQL 8.4 with 10,000 sessions (3,333 completed and 6,667 active),
owner-only listing used idx_flashcard_session_owner plus filesort; lesson-filtered
listing used the lesson FK index plus filesort. V11 adds (user_subject, created_at,
id) and (user_subject, lesson_id, created_at, id). Owner-only listing then uses a
backward ordered index scan; lesson filtering can use either ordered index
depending on selectivity, and its count query can use the covering lesson index.
The existing owner/status/date index remains for status-filtered listing.
These are query-plan checks, not production load benchmarks.