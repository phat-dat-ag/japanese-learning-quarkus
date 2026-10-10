# Kanji Context Quiz JSON import (Phase 3)

`POST /api/v1/admin/kanji-quiz/questions/import` requires a JWT with the exact `Admin`
role. Send `multipart/form-data` with one uploaded `file` part containing a nonempty
JSON array of Phase 2 `QuizQuestionCreateRequest` objects. Both EXAMPLE and CUSTOM are
supported. There is no batch envelope or publication flag. The merged `/q/openapi`
contains full request examples under **Admin Kanji Quiz**.

## Partial-success response

An accepted array returns HTTP 200 in the existing API response envelope, even if
some or all items fail. Its `data` is:

```json
{
  "total": 2,
  "imported": 1,
  "failed": 1,
  "results": [
    {"index": 0, "status": "IMPORTED", "questionId": 11, "version": 1},
    {"index": 1, "status": "FAILED", "errors": [
      {"field": "[1].content.targetReading", "code": "QUIZ_QUESTION_INVALID",
       "message": "Target reading must exactly match the selected segment"}
    ]}
  ]
}
```

`total = imported + failed = results.size`. Every item has its original zero-based
index, in input order. IMPORTED results contain committed identifiers and opaque
version tokens; FAILED results contain errors and omit identifiers/version.
All-item failure still has a successful HTTP envelope: consult `failed` and `results`.

Multiple Bean Validation errors are collected and sorted by field within each item.
JSON decoding and domain validation may stop at the first error for that item.
Paths include `[0].options[2].text` and `[1].content.targetReading`. Domain range errors
anchor at `.content.targetStart`, source errors at `.content.sourceType`, and missing
references/duplicate conflicts at `.content`, retaining safe Phase 2 messages/codes.
Item shape/Bean errors use `QUIZ_ITEM_INVALID`; JSON mapping errors use
`QUIZ_ITEM_JSON_INVALID`; business errors retain `QUIZ_QUESTION_INVALID`,
`QUIZ_REFERENCE_NOT_FOUND`, or `QUIZ_QUESTION_CONFLICT`.

## Request validation and limits

The file part accepts `application/json` or `application/octet-stream`; its filename
and extension do not determine validity. Async file I/O and worker-thread parsing
finish before any persistence. The entire JSON document must be syntactically valid.
Malformed syntax (including a malformed tail), duplicate JSON object keys, trailing
JSON, a non-array root, an empty array, or excessive item count reject the whole file.
Unknown fields, wrong scalar types, null/non-object items and invalid DTOs instead
produce FAILED item results. Scalar coercion is disabled.

| Property | Environment variable | Default | Failure |
| --- | --- | --- | --- |
| `quiz.import.max-file-bytes` | `QUIZ_IMPORT_MAX_FILE_BYTES` | 1048576 (1 MiB) | 413 |
| `quiz.import.max-items` | `QUIZ_IMPORT_MAX_ITEMS` | 100 | 400 |

Both limits must be positive; max-file-bytes must fit int32. Exact limits are accepted.
Size is checked before reading and parsing; parsing stops at the item limit.
Server/proxy upload limits also apply; increasing this limit does not alter those
limits or other imports.

Request-level errors process no items:

- 400 `QUIZ_IMPORT_INVALID`: malformed/unsupported file, invalid root or count.
- 400 `BAD_REQUEST`: missing multipart file.
- 401/403: existing authentication and Admin authorization errors.
- 413 `PAYLOAD_TOO_LARGE`: file size or earlier server/proxy request limit.
- 415 `UNSUPPORTED_MEDIA_TYPE`: request is not multipart/form-data.

## Transactions and business rules

`QuizImportService` has no outer session or transaction annotation. It processes
items sequentially through the CDI-intercepted `QuizAdminWriteService.create`.
Each call opens and closes its own Hibernate Reactive transaction/session. Success
is recorded only after commit. Expected validation, missing-reference and conflict
failures are recovered outside that interceptor, after rollback/session cleanup.
A failed question leaves no options, classifications or question row; preceding and
following successful items remain committed. Auto-increment gaps are normal.

Unexpected infrastructure failures, including connection failures, are not converted
into ordinary item errors: they stop processing and produce the existing sanitized
server error. Earlier commits remain. A client retry may encounter duplicates and
should inspect the question bank when recovering from such an interrupted request.

All business rules reuse Phase 2. Every imported question is DRAFT. EXAMPLE reads its
Vocabulary-owned source without modifying it; CUSTOM owns its reading. Different
targets from one example are allowed. The existing V7 identity UNIQUE constraint and
Phase 2 locking protect duplicates against the database, within a batch and across
concurrent imports/creates. The first committed duplicate wins; later duplicates
become FAILED results. Existing publication/invalidation rules remain unchanged.
No schema changes, privileged triggers, Player APIs, UI or sample questions are added.
Existing Vocabulary and Lesson import contracts are unchanged.

## Verification

`QuizImportMysqlTest` runs against disposable MySQL with configurable limits of 4096
bytes and three items. It covers both sources, mixed/all-success/all-failed batches,
ordered indexed errors, multiple validation errors, source ownership, duplicates,
concurrent imports and single creates, file rejection before writes, exact limits
and authorization. A temporary test-only CHECK constraint forces failure after
partial question insertion, proving rollback isolation and subsequent-item progress.
`QuizImportServiceTest` confirms unexpected and database connection failures propagate.
Merged `/q/openapi` tests verify the multipart contract, schemas, examples, security,
limits and partial-success response. Run Maven verify with the existing opt-in MySQL
settings and a database migrated through V7, plus Flyway validate and git diff --check.
