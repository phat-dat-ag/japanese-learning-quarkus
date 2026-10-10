# Kanji Quiz progress and statistics (Phase 5.2)

Both GET endpoints require the exact verified JWT `sub` and case-sensitive User or
Admin role. Admin has no ownership bypass. Neither endpoint has parameters or a
request body; client-supplied userId never selects an owner. Responses use ApiResponse.

- `/api/v1/kanji-quiz/progress`: completedSessions, answeredCount, correctCount,
  incorrectCount, accuracyPercentage, bestScore, averageScore, latestCompletedAt.
- `/api/v1/kanji-quiz/progress/breakdown`: independent `levels` and `lessons` arrays.
  Each bucket has classification, levelId, lessonId, completedSessions, answeredCount,
  correctCount, incorrectCount, accuracyPercentage.

Only COMPLETED sessions count, including for latestCompletedAt. Overall counts each
session/answer once. Incorrect = answered - correct. Accuracy = 100 * correct / answered,
weighted by answers. Score units match history: one point per correct answer; bestScore
is the maximum session score and averageScore is total correct / completedSessions.
Accuracy and average use two decimal places, HALF_UP. Zero denominators return zero.
Empty history has all numeric values zero, latestCompletedAt null, and empty breakdown
arrays. Timestamps use the existing UTC LocalDateTime representation without offsets.

## Immutable classifications and overlapping buckets

V8 adds a captured flag (false for legacy snapshots) and two classification tables.
New sessions capture all explicit question levels, all lessons with their then-current
level, and the union of explicit and lesson-implied levels. Primary keys deduplicate
repeated classifications. CUSTOM and EXAMPLE use the same rules. There are no foreign
keys to mutable classification master data, so later edits/deletions cannot alter history.
Capture occurs atomically during creation under existing question locks, after snapshots
are flushed. INSERT SELECT uses current locking reads. No GET captures or backfills data.

UNKNOWN means never captured, including pre-V8 sessions still in progress at migration.
UNASSIGNED means captured with no assignment in that dimension. Both have null IDs;
UNKNOWN is never inferred from today's bank. A level-only question is ASSIGNED in levels
and UNASSIGNED in lessons. No synthetic lesson is created. Lesson buckets use captured
(levelId, lessonId); the same lesson moved between levels may have multiple historical
buckets. Level buckets always have lessonId null.

Each session counts once in every bucket containing one of its questions. Each answer
counts once per distinct assigned classification. Multiple assignments and mixed sessions
therefore overlap: neither session nor answer bucket totals are additive. Use overall
progress for totals. Multiple lessons implying the same level count only once for that
level. Independent dimension queries avoid cross products. Sort order is ASSIGNED,
UNASSIGNED, UNKNOWN, then levelId ascending, lessonId ascending. Absent buckets are omitted.

## Queries and compatibility

Overall uses one database aggregate over per-session scores; breakdown uses two grouped
queries in one reactive transaction. Only aggregate rows reach Java, never whole histories.
No bank, vocabulary or example data is read by GETs. quiz_user_progress is unsuitable for
these reports: it has no completion dimension, depends on mutable bank IDs, and gameplay
currently does not populate it. No redundant persisted aggregates are introduced.

Existing gameplay and history contracts remain unchanged, including null original
selection filters in history. Question classifications are not original session filters.
The only creation change is additional atomic snapshot persistence. Authentication errors
are 401/403; unexpected failures use the sanitized 500 envelope. Full schemas, examples,
rounding/counting rules and errors are in the merged `/q/openapi`.

Focused tests: QuizProgressMysqlTest and QuizProgressOpenApiTest. Full Maven regression
is deferred to Phase 5.3. No UI, gameplay features, or Phase 5.3 hardening is included.
