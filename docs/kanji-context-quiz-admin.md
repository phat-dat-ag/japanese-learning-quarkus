# Kanji Context Quiz Admin APIs (Phase 2)

All operations use `/api/v1/admin/kanji-quiz/questions`, a JWT with the exact `Admin`
role, the existing ApiResponse/error envelope, and HTTP 200 on success (project convention).
The merged `/q/openapi` is the complete schema/example contract under **Admin Kanji Quiz**.

| Method | Suffix | Behavior |
| --- | --- | --- |
| POST | empty | Create a DRAFT with `content` and four `options` |
| GET | empty | Paged bank with filters; includes correct answers (Admin only) |
| GET | `/{id}` | Complete details, stable option IDs, version and source validity |
| PUT | `/{id}` | Replace `content` using the current `version`; retain option IDs |
| PUT | `/{id}/options/{optionId}` | Change one owned option's `text` using `version` |
| PUT | `/{id}/correct-option` | Select owned `optionId` using `version` |
| POST | `/{id}/publish` | Validate/publish or explicitly revalidate, using `version` |
| POST | `/{id}/unpublish` | Set DRAFT, including restoring an archive, using `version` |
| POST | `/{id}/archive` | Exclude new games and retain history, using `version` |

## Content and lifecycle

`content` includes sourceType, exampleId, vocabularyId, sentenceReading, targetStart,
targetLength, targetReading, explanationVi/explanationEn, levelIds and lessonIds.
Source type is immutable. EXAMPLE requires an existing exampleId and forbids a supplied
sentenceReading; CUSTOM requires its own sentenceReading and forbids exampleId.
Both allow an optional vocabularyId; EXAMPLE must reference an example assigned to it.
Required classification arrays can be empty and replace all assignments on PUT.
Omitted optional content fields are cleared. PUT is a full content replacement, not PATCH.
Options are edited separately and never replaced or reordered by content updates.

Create and content edits validate target ranges and all four options even while DRAFT.
Exactly one correct option is required; option text is nonblank, has no surrounding
whitespace, and must be distinct after NFKC normalization. Source readings are never
normalized. Question content and option limits count Unicode code points; offsets do not count
UTF-16 units. See the Phase 1 document for precise kana/voicing rules.

Successful content, option-text and correct-answer writes demote to DRAFT. Publication
locks and validates the live example, then records its fingerprint. A changed/deleted
example remains unavailable to new games regardless of editorial status. The detail/list
response reports `sourceInvalidated` (also true for never-validated EXAMPLE drafts) and
`eligibleForNewGames`; deleted examples omit reading/reference fields. Restore a detached
question by changing its exampleId and validating again, or archive it.
Archived questions must be explicitly unpublished before editing or publishing.
All existing session snapshots remain independent of these edits.

## Concurrency, duplicates and errors

Every mutation except creation requires the response's current `version`. The service
locks the parent question before options and sources, checks the version, and performs
all changes/validation in one reactive transaction. Versions are opaque revision tokens;
a command may advance them more than once due to required intermediate flushes. Even an
option-only change advances the parent version. Failed writes roll back demotion and edits.
Correct-answer replacement clears the previous unique correct marker before setting the
new one, within the same transaction. Readers never see the intermediate state.

V7 adds one nullable `content_key` and a UNIQUE constraint. Admin creates and content updates calculate SHA-256
from source type, source identity (EXAMPLE ID or exact CUSTOM reading), targetStart and
targetLength. Different targets are allowed. Same source/target is a duplicate even with
different explanations, classifications, vocabulary, options or ARCHIVED status. Restore
or edit that question instead. The service also checks legacy rows with NULL keys. V7 does
not rewrite legacy data or alter V1-V6. Concurrent new Admin duplicates are rejected by
the unique key; future Quiz writers must reuse this service to maintain identity keys.
Arbitrary direct SQL remains outside the domain guarantees, as agreed in Phase 1.

- 400: BAD_REQUEST (JSON/Jakarta validation), QUIZ_QUESTION_INVALID (domain/filter rules).
- 401/403: existing UNAUTHORIZED/FORBIDDEN; all nine operations require Admin.
- 404: QUIZ_QUESTION_NOT_FOUND, QUIZ_OPTION_NOT_FOUND (including wrong owner),
  QUIZ_REFERENCE_NOT_FOUND. Unparseable numeric path/query values use existing NOT_FOUND.
- 409: QUIZ_QUESTION_CONFLICT (stale version, duplicate, immutable source type, archived
  edit, or concurrent database reference/constraint conflict). Reload before retrying.

Filters: sourceType, status, levelId, lessonId, vocabularyId, exampleId and keyword.
Filters combine with AND. Lesson classification also implies its current JLPT level;
when both are requested the lesson must belong to that level. Keyword is a trimmed literal
substring of owned/source reading, targetReading, or VI/EN explanations (not option text).
Blank keywords are ignored. Paging is zero-based, size 1-100, newest ID first, and the
page range must fit int32. Unmatched filters or pages return an empty items list.
Related response data is fetched in batches, not per question.

## Verification and boundaries

`QuizAdminMysqlTest` exercises actual HTTP/JWT/MySQL writes, ownership, rollback,
concurrent duplicates/version conflicts, filters, source invalidation and snapshots.
`QuizAdminOpenApiTest` checks merged `/q/openapi`, including deserializing the examples
and validating their Unicode targets/options. Existing exhaustive OpenAPI tests include
all new operations. Run with the disposable MySQL profile documented in Phase 1,
migrated through V7, then Maven verify and git diff --check.

No file import, Player API, UI, Vocabulary endpoint changes or database triggers are added.
Player authorization, randomization and answer/progress orchestration remain future work.
