# Admin vocabulary editing

All paths below start with `/api/v1/admin/vocabularies/{vocabularyId}`.
All operations require a Bearer JWT with the `Admin` role. Unauthenticated callers
receive 401; authenticated non-Admins receive 403. Existing import routes are unchanged.

## Contract

Use `Content-Type: application/json`. Every POST body is an array of **1 to 100** items,
including when adding just one item. Every PUT body is one complete object for the
specified aspect; it does not replace the vocabulary aggregate or any collection.
No DELETE or assignment-removal operation is provided.

All successful operations return HTTP 200 with the existing `ApiResponse` envelope:

```json
{"success":true,"data":{"id":42},"meta":{"timestamp":"...","traceId":"...","correlationId":"..."}}
```

POST returns `data: [{"id":42}, {"id":43}]` in request order. IDs identify the created
children. For level, lesson, POS, and kanji assignments, the ID is the referenced
master ID; the vocabulary ID completes the association identity. These response
DTOs never expose persistence entities. GET `/api/v1/flashcards/{id}` remains available
for loading the vocabulary's current data.

| Method | Path suffix | Body fields per item | Behavior |
|---|---|---|---|
| PUT | (base path) | `word`, `normalizedWord` | Update existing core only; unique normalized word |
| POST | `/readings` | `reading`, `isPrimary`, `displayOrder` | Add owned readings |
| PUT | `/readings/{readingId}` | Same | Update one owned reading |
| POST | `/meanings` | `language`, `meaning`, `isPrimary`, `displayOrder` | Add owned meanings |
| PUT | `/meanings/{meaningId}` | Same | Update one owned meaning |
| POST | `/pitch-accents` | `readingId`, `accentPattern` | Referenced reading must belong to vocabulary |
| PUT | `/pitch-accents/{pitchAccentId}` | Same | Both existing accent and requested reading must belong to vocabulary |
| POST | `/examples` | `japaneseText`, `japaneseReading`, `meaningVi`, `meaningEn`, `targetText`, `displayOrder` | Add sentences and associations |
| PUT | `/examples/{exampleId}` | Same | Update associated sentence and local association, subject to shared-content protection |
| POST | `/levels` | `level` (code), `displayOrder` | Assign existing master levels |
| PUT | `/levels/{levelId}` | `displayOrder` | Reorder existing assignment only |
| POST | `/lessons` | `lessonId`, `displayOrder` | Assign existing lesson; its level must already be assigned |
| PUT | `/lessons/{lessonId}` | `displayOrder` | Reorder existing assignment only |
| POST | `/parts-of-speech` | `code` | Assign existing POS master records; no editable link fields |
| POST | `/kanji` | `character`, `strokeCount`, `meaningVi`, `meaningEn`, `displayOrder` | Create new character or attach existing character with matching metadata |
| PUT | `/kanji/{kanjiId}` | Same | Edit exclusively associated metadata or shared association order |
| POST | `/kanji/{kanjiId}/readings` | `reading`, `readingType`, `displayOrder` | Add readings only to exclusively associated kanji |
| PUT | `/kanji/{kanjiId}/readings/{readingId}` | Same | Validate full vocabulary -> kanji -> reading chain and exclusive kanji association |

Fields are required except nullable `strokeCount`, `meaningVi`, and `meaningEn` in
kanji metadata. PUT clears those optional fields when null/omitted. Other omitted
or null fields are rejected. Text lengths follow existing columns. Display orders
are nonnegative; lesson association display orders are positive. Pitch patterns
and non-null stroke counts are integers in 0 to 65535, matching unsigned SMALLINT storage.
Meaning language is `vi` or `en`. `readingType` remains a nonblank string up to 20
characters: the existing schema/domain does not define an enum (ON/KUN are common values).

## Domain rules discovered from the schema

- Vocabulary has a unique normalized word. Readings belong to vocabulary, with a
  unique `(vocabulary_id, reading)` pair. A vocabulary must retain at least one
  primary reading, as required by the importer. Multiple primaries are allowed by
  the existing domain; editing never silently promotes/demotes another reading.
- Meanings belong to vocabulary. Neither schema nor importer imposes a one-primary
  rule per language or vocabulary. Editing preserves this behavior and rejects
  repeated `(language, meaning)` content within a vocabulary.
- Levels and POS are shared master data. Lesson identity is `(level_id, lesson_number)`.
  Assignment tables have composite PKs; levels and lessons also have display order.
  No levels, lessons, or POS records are created. Updating association identity would
  remove an existing assignment, so only ordering is editable. POS has no PUT endpoint.
- Kanji is global/shared with a unique character. Kanji readings belong to that shared
  entity and have unique `(kanji_id, reading, reading_type)`. Vocabulary nesting does
  not establish ownership. Metadata changes and all kanji-reading writes return 409
  when another vocabulary references the kanji. Existing character attachment never
  overwrites metadata. Matching shared metadata may be resubmitted to change only
  the local association's display order.
- Example sentences can also be shared. Sentence-content changes return 409 when
  another vocabulary references the sentence; local `targetText`/`displayOrder`
  changes remain allowed. Example identity within a vocabulary is exact
  `(japaneseText, japaneseReading, targetText)`, matching import deduplication.
  Editing returns 409 for duplicate ADD or conflicting UPDATE; it does not silently
  skip them. PUT always addresses the specified example ID.

No migrations or database constraints were changed. Existing content and assignment
constraints remain the final database protection.

## Errors and transactions

Existing `ApiResponse` / `ErrorResponse` / trace metadata conventions apply:

- 400: invalid JSON, body shape, required/length/range validation, invalid language,
  removal of the last primary reading, or unassigned lesson level.
- 404: missing vocabulary, child, association, or referenced master record. Foreign
  child IDs also return 404 without disclosing another vocabulary's data.
- 409: duplicate value/assignment or attempted shared-content mutation. Code:
  `VOCABULARY_EDIT_CONFLICT`. No SQL or constraint details are returned.

Each service operation uses one reactive `@WithTransaction` boundary. A vocabulary
row lock serializes writes to the same vocabulary through these edit APIs. All batch
writes roll back on any failure. Kanji writes also lock the shared kanji, with a
current locking read for other associations. Queries remain in repositories; resource
classes only handle HTTP concerns. Services are separated by core, readings, meanings,
assignments, pitch accents, examples, and kanji. Import business classes are not reused.

Known unique-constraint failures are mapped narrowly; unrelated persistence exceptions
are not swallowed. Meanings/example content and primary flags lack database uniqueness
constraints. Imports or external writers that do not follow these edit locks can still
race; concurrent external association changes remain a limitation. Concurrent multi-row
operations can encounter ordinary database deadlocks/timeouts and must be retried by
callers after failure; no distributed locking or automatic transaction retries were added.

## Verification

`AdminVocabularySecurityTest` covers every route's 401/403 behavior, array validation,
and OpenAPI responses. `AdminVocabularyEditMysqlTest` runs against the existing opt-in
MySQL test profile with real repositories/transactions. It covers all sections, successful
updates, full ownership checks, duplicates, rollback, shared-data safeguards, primary
semantics, and representative simultaneous edits.

Use a disposable database initialized with migrations V1 through V5, as described in README:

```powershell
.\mvnw.cmd verify "-Dvocabulary.mysql.tests=true" `
  "-Dvocabulary.mysql.url=mysql://127.0.0.1:<port>/vocabulary_import_test"
```

The default test password is `vocabulary-test`; override `vocabulary.mysql.password`
for a different disposable test database. These tests must not target production.
