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
{"success":true,"data":{"readingId":42},"meta":{"timestamp":"...","traceId":"...","correlationId":"..."}}
```

POST returns a result array in request order, for example
`data: [{"readingId":42}, {"readingId":43}]`. Each section has a distinct result DTO:

| Section | Result ID field |
|---|---|
| Core | `vocabularyId` |
| Readings | `readingId` |
| Meanings | `meaningId` |
| Pitch accents | `pitchAccentId` |
| Examples | `exampleId` (the example sentence ID) |
| Levels | `levelId` |
| Lessons | `lessonId` |
| Parts of speech | `partOfSpeechId` |
| Kanji | `kanjiId` |
| Kanji readings | `kanjiReadingId` |

For assignments, the ID is the referenced master ID; the vocabulary ID completes
the association identity. These DTOs never expose persistence entities.
GET `/api/v1/flashcards/{id}` remains available for loading current vocabulary data.
This completes the WIP response contract: clients using the checkpoint's generic
`id` field must switch to the domain-specific field above. Paths and request JSON
fields are unchanged.

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

## Schema and runtime investigation

The continuation starts at `7493932` (`wip: implement admin vocabulary editing APIs`)
on `feature/admin-vocabulary-edit`. The WIP already implemented all 18 routes,
section services, ownership checks, shared-data protection, row locking, request
validation, OpenAPI, and 47 opt-in MySQL tests. Remaining work was runtime/schema
verification, precise response naming, readable flows, and additional regression
coverage; no endpoint redesign was needed.

V1 defines `kanji.stroke_count` as nullable `SMALLINT UNSIGNED` and
`vocabulary_pitch_accents.accent_pattern` as non-null `SMALLINT UNSIGNED`. No migration
through V5 changes them. Both Java fields were already `Integer` before the WIP;
the WIP did not edit either entity or the migrations. Consequently, the schema
warnings are a pre-existing source mapping mismatch, not evidence of a stale
transferred database.

On a disposable MySQL 8.4 database migrated by Flyway from V1 to V5, the original
WIP passed its 47 persistence tests. Explicitly running Quarkus post-boot validation
reproduced both warnings and the suggested INTEGER alterations, but the subsequent
transactional endpoint returned 200. The reported `UnknownServiceException` was
not reproduced. Its missing service name and full original stack trace are still
needed to establish that separate failure's root cause; the schema fix is not
claimed to fix it.

The two mappings now declare the base SQL type `smallint`. Hibernate Reactive's
MySQL metadata reader exposes that base type without `unsigned`, so declaring
`smallint unsigned` still produced a false mismatch in the tested runtime.
Flyway remains responsible for unsigned storage and schema creation. Java remains
`Integer`, avoiding signed `Short` narrowing. Tests verify the actual unsigned
column types, clean post-boot validation, and reads/writes at 0, 32768, and 65535.
Schema validation remains enabled; application configuration and dependencies
are unchanged. No new migration or database alteration is required.

For an existing local database, compare `flyway_schema_history` with V1–V5 and
inspect `SHOW COLUMNS` for these two columns. Apply only genuinely missing existing
migrations through the normal orchestration. Do not run Hibernate's suggested
ALTER statements. After updating the mappings, restart the application to verify
clean validation and an Admin write. If HTTP 500 persists, capture the complete
missing-service exception and any earlier bootstrap/reload errors.

## Manual smoke checks

1. Start against a disposable database with V1–V5 applied; check startup validation.
2. Call an edit route without a token (401), with User (403), and with Admin (200).
3. POST one-element and multiple-element arrays for each collection; confirm the
   corresponding domain ID fields and unchanged unrelated sections using flashcard GET.
4. PUT an owned child, then the same child under another vocabulary (404); test the
   complete vocabulary/kanji/reading chain too.
5. Submit a valid first item followed by a duplicate or missing reference; verify
   409/404 and that none of the request's additions remain.
6. Attach existing kanji to a second vocabulary. Metadata and reading edits must
   return 409, while unchanged metadata plus local display-order edits succeed.
7. Check shared examples reject sentence changes but permit local target/order edits.
8. Confirm level/lesson PUT changes only ordering, an unassigned lesson level is
   rejected, and no POS PUT or DELETE operation is documented.

## Verification

`AdminVocabularySecurityTest` covers every route's 401/403 behavior, error envelopes, array validation,
and OpenAPI responses. `AdminVocabularyEditMysqlTest` runs against the existing opt-in
MySQL test profile with real repositories/transactions. It covers all sections, successful
updates, full ownership checks, duplicates, rollback after late missing references,
shared-data safeguards, sibling isolation, unsigned storage boundaries, schema validation,
primary semantics, and representative simultaneous edits.

Use a disposable database initialized with migrations V1 through V5, as described in README:

```powershell
.\mvnw.cmd verify "-Dquarkus.http.test-port=0" "-Dvocabulary.mysql.tests=true" `
  "-Dvocabulary.mysql.url=mysql://127.0.0.1:<port>/vocabulary_import_test"
```

The default test password is `vocabulary-test`; override `vocabulary.mysql.password`
for a different disposable test database. These tests must not target production.

Continuation verification (2026-09-28):

- Focused admin/security/OpenAPI run: 126 passed.
- Full `mvnw.cmd -B verify` with `-Dquarkus.http.test-port=0`,
  `-Dvocabulary.mysql.tests=true`, and the disposable MySQL URL: **255 passed,
  zero failures/errors/skipped tests; BUILD SUCCESS**.
- Real MySQL subset: 68 admin-edit tests and 12 existing importer tests, all passed.
- MySQL 8.4 was initialized through Flyway V1–V5; migrations and database constraints
  were not modified. The schema regression validates the real unsigned columns.
- Failsafe uses the existing `skipITs=true` default. The opt-in real MySQL tests run
  under Surefire and were enabled for the full verification above.
- No JWT/JWKS, application configuration, build dependencies, Angular, or root
  repository changes were made. The only pre-existing non-feature source changes
  are the two entity column mappings required by the schema investigation.

## Files changed after the WIP checkpoint

Modified existing files:

- `docs/admin-vocabulary-edit.md`
- `src/main/java/com/japaneselearning/vocabulary/admin/resource/AdminVocabularyAssignmentResource.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/resource/AdminVocabularyCoreResource.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/resource/AdminVocabularyExampleResource.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/resource/AdminVocabularyKanjiResource.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/resource/AdminVocabularyMeaningResource.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/resource/AdminVocabularyPitchAccentResource.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/resource/AdminVocabularyReadingResource.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/service/VocabularyAssignmentEditService.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/service/VocabularyCoreEditService.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/service/VocabularyExampleEditService.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/service/VocabularyKanjiEditService.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/service/VocabularyMeaningEditService.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/service/VocabularyPitchAccentEditService.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/service/VocabularyReadingEditService.java`
- `src/main/java/com/japaneselearning/vocabulary/entity/Kanji.java`
- `src/main/java/com/japaneselearning/vocabulary/entity/VocabularyPitchAccent.java`
- `src/main/java/com/japaneselearning/vocabulary/repository/KanjiRepository.java`
- `src/main/java/com/japaneselearning/vocabulary/repository/VocabularyMeaningRepository.java`
- `src/main/java/com/japaneselearning/vocabulary/repository/VocabularyPitchAccentRepository.java`
- `src/main/resources/META-INF/openapi.yaml`
- `src/test/java/com/japaneselearning/security/AdminVocabularyEditMysqlTest.java`
- `src/test/java/com/japaneselearning/security/AdminVocabularySecurityTest.java`

New files (including renamed request DTOs):

- `src/main/java/com/japaneselearning/vocabulary/admin/dto/AssignmentOrderEdit.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/KanjiReadingResult.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/LessonAssignmentAdd.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/LevelAssignmentAdd.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/PartOfSpeechAssignmentAdd.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/VocabularyCoreEdit.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/VocabularyCoreResult.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/VocabularyExampleEdit.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/VocabularyExampleResult.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/VocabularyKanjiResult.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/VocabularyLessonResult.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/VocabularyLevelResult.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/VocabularyMeaningEdit.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/VocabularyMeaningResult.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/VocabularyPartOfSpeechResult.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/VocabularyPitchAccentEdit.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/VocabularyPitchAccentResult.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/VocabularyReadingEdit.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/VocabularyReadingResult.java`
- `src/test/resources/META-INF/services/org.hibernate.integrator.spi.Integrator`

Replaced old DTO files (nine request renames and the generic result):

- `src/main/java/com/japaneselearning/vocabulary/admin/dto/CoreEdit.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/ExampleEdit.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/LessonAdd.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/LevelAdd.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/MeaningEdit.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/OrderEdit.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/PitchAccentEdit.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/PosAdd.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/ReadingEdit.java`
- `src/main/java/com/japaneselearning/vocabulary/admin/dto/VocabularyEditResult.java`
