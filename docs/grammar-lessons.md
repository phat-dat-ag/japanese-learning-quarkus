# Grammar lesson management

The existing lesson catalogue and vocabulary assignments are unchanged. Lessons
remain shared data under an existing JLPT level; no separate grammar lesson ID,
owner, draft or publication state is introduced. Apply
`V12__create_lesson_contents.sql` externally before deploying this change.

## Endpoints

All responses use the existing `ApiResponse` envelope and trace/correlation metadata.
Successful writes return HTTP 200. Roles are case-sensitive JWT `role` values.

| Method | Route | Roles | Result |
| --- | --- | --- | --- |
| GET | `/api/v1/lessons?level=N5` | User/Admin | Existing ordered, unpaginated metadata array |
| GET | `/api/v1/lessons/{lessonId}` | User/Admin | Existing four-field `LessonResponse`; no content load |
| POST | `/api/v1/lessons` | Admin | Existing metadata creation |
| PUT | `/api/v1/lessons/{lessonId}` | Admin | Existing metadata replacement |
| POST | `/api/v1/lessons/batch` | Admin | Existing metadata-only, independently committed batch |
| DELETE | `/api/v1/lessons/{lessonId}` | Admin | Deleted metadata; referenced lessons return 409 |
| GET | `/api/v1/lessons/{lessonId}/content` | User/Admin | Document and `ETag` |
| PUT | `/api/v1/lessons/{lessonId}/content` | Admin | Created/replaced document and new `ETag` |
| DELETE | `/api/v1/lessons/{lessonId}/content?expectedVersion=1` | Admin | Removed document; requires `If-Match` |
| POST | `/api/v1/lessons/content/import?mode=SKIP_EXISTING` | Admin | Multipart JSON import report |

Metadata retains `id`, `lessonNumber`, `title`, and optional `description`. The
content response has `lessonId`, `schemaVersion`, `version`, `content`, `createdAt`,
and `updatedAt`. Content timestamps are UTC `LocalDateTime` strings at microsecond
precision; an offset suffix is not emitted. Existing lesson timestamps are unchanged.

## Authoritative document schema v1

`META-INF/openapi.yaml` defines the machine-readable contract, including the
`LessonContentBlock` discriminated union. `LessonContentDocument` and its nested
records in `vocabulary/dto/content/` define the Java representation.

A document is exactly `{"blocks": [...]}`. Unknown fields are rejected at every
level. Every block requires a unique `id` matching `[A-Za-z0-9_-]{1,64}` and one
of the following exact `type` values. Every field below is required; only the
table's **headers array may be empty**. No block contains other blocks.

| Type | Fields in addition to `id` and `type` |
| --- | --- |
| `heading` | `level`: integer 1–6; `inlines`: inline array |
| `paragraph` | `inlines`: inline array |
| `list` | `ordered`: boolean; `items`: array of inline arrays |
| `table` | `headers`: array of cells; `rows`: array of rows, each an array of cells; a cell is an inline array |
| `example` | `japanese`, `reading`: nonblank text; `translations`: exactly nonblank `en` and `vi` strings |
| `callout` | `variant`: `note`, `tip`, `warning`, or `information`; `inlines`: inline array |
| `quote` | `inlines`: inline array |
| `divider` | No additional fields |

An inline is exactly `{"text":"日本語","marks":["bold"]}`. `marks` is required,
may be empty, and contains unique values from `bold`, `italic`, `underline`.
Inline whitespace is allowed, so rich-text runs can preserve spaces between words.
Text must be nonempty, with at most 10,000 UTF-16 code units. Example strings must
also be nonblank. Angle brackets and control characters other than tab/CR/LF are
rejected. Strings are plain text, never executable HTML/JavaScript/CSS. The future
renderer must bind text as text and render only allowlisted components/marks.
There are no links, scripts, style properties, media, or HTML blocks.

Limits:

- 0–200 blocks; empty blocks is a valid existing document.
- 1–100 inlines per inline array; 0–3 marks per inline.
- 1–100 list items; no nested lists.
- 1–100 table rows, 1–20 columns. All rows have equal width. Headers are empty
  or have exactly that width. Every header/cell contains an inline array.
- Maximum compact encoded document size: 1 MiB. The direct PUT parser also bounds
  token count to 100,000 and JSON depth to 12, including the request envelope.
- Numeric and boolean values are not coerced from strings; duplicate JSON keys,
  null array elements, trailing JSON values, and unknown subtypes are rejected.

Example create request:

```json
{
  "schemaVersion": 1,
  "expectedVersion": 0,
  "content": {
    "blocks": [
      {"id":"h-1","type":"heading","level":2,"inlines":[{"text":"Trợ từ は","marks":["bold"]}]},
      {"id":"e-1","type":"example","japanese":"私は学生です。","reading":"わたしはがくせいです。","translations":{"en":"I am a student.","vi":"Tôi là học sinh."}}
    ]
  }
}
```

## Revisions and deletion

`schemaVersion=1` describes the document format, independently of editing `version`.
There is no automatic conversion of unsupported versions and no revision history.

- Create with `expectedVersion=0` and no `If-Match`; only an absent document can
  be created. The response revision is 1.
- Update with positive `expectedVersion` and the exact `ETag` returned by GET/PUT
  in the `If-Match` header. Successful replacement increments the revision.
- Delete with positive `expectedVersion` query parameter and the exact current
  `If-Match`. Missing/mismatched preconditions return 409; no wildcard/weak ETags.
- ETags include lesson identity, creation timestamp and revision. This distinguishes
  a recreated document at revision 1 from a document an older editor had loaded.
- Each mutation locks the parent lesson row and performs checks and writes in one
  reactive transaction. The application owns revisions; the entity has no `@Version`.
- Deleting content preserves lesson metadata and assignments. Repeated deletion
  returns 404 `LESSON_CONTENT_NOT_FOUND`; it is not a successful no-op.
- Deleting a lesson rejects vocabulary/quiz assignments, flashcard sessions, and
  historical quiz lesson snapshots. Only its optional content may cascade. The
  existing foreign-key rules remain unchanged, and deletion has a database guard.

Missing lesson: 404 `RESOURCE_NOT_FOUND`. Existing lesson without content: 404
`LESSON_CONTENT_NOT_FOUND`. A document with empty blocks is still present.
Other errors include 400 `BAD_REQUEST`, `LESSON_CONTENT_INVALID`, or
`LESSON_CONTENT_SCHEMA_UNSUPPORTED`; 409 `LESSON_CONTENT_CONFLICT` or
`LESSON_CONFLICT`; 413 `PAYLOAD_TOO_LARGE`; and the existing 401/403/500 codes.
Validation details contain safe field paths and messages, not submitted text.

## Complete lesson JSON import

Upload multipart field `file` to `/api/v1/lessons/content/import`. The query `mode`
defaults to `SKIP_EXISTING`; the only alternative is `UPDATE_EXISTING`. The UTF-8
file is a strict JSON array. A complete two-lesson file is provided in
[`grammar-lessons-import-example.json`](grammar-lessons-import-example.json).
Each item contains:

```json
{
  "level": "N5",
  "lessonNumber": 1,
  "title": "Trợ từ は",
  "description": "Topic marker",
  "displayOrder": 1,
  "schemaVersion": 1,
  "expectedVersion": 0,
  "content": {"blocks": []}
}
```

`description` is optional/null; `etag` is optional except when replacing an existing
document. All other fields are required. Level codes are trimmed and resolved to
existing levels; lessons are identified by resolved `(level_id, lesson_number)`.
Metadata uses the existing validation and uniqueness rules.

Before **any writes**, the entire file is parsed and validated, including every
document, duplicate lesson keys, duplicate display orders within a level, unknown
levels and display orders already occupied by a different lesson. A failure in
this phase rejects the whole request. Reordering lessons by swapping occupied
display orders in one file is not supported.

After preflight, items execute sequentially in independent transactions. New
lesson metadata and content commit together; content failure rolls back metadata.
An item failure does not roll back previously committed independent items.

- `SKIP_EXISTING`: an existing lesson is skipped without changing metadata/content,
  even if its content is absent. All uploaded fields still undergo preflight.
- `UPDATE_EXISTING`: an existing lesson's metadata and content are replaced in one
  transaction. Supply positive `expectedVersion` and `etag` copied verbatim from
  GET content, including quotation marks. For absent content use revision 0 and no
  `etag`. Stale/missing preconditions fail that item without changing metadata.
- A missing lesson is created; its content must use revision 0 and no `etag`.
- Imports lock the existing JLPT level before looking up the lesson key, so concurrent
  imports create once and subsequent `SKIP_EXISTING` imports skip it. Database
  uniqueness remains authoritative for races with ordinary lesson operations.
  Conflicts fail that item; no automatic overwrite or retry occurs.
- Retrying default imports skips prior successes and retries other independent
  items. Retrying an update with an already-consumed revision fails safely; refresh
  revisions/ETags before intentionally updating again. No idempotency key is stored.

HTTP 200 reports `total`, `created`, `updated`, `skipped`, `failed`, and ordered
`results`. Each result includes zero-based `index`, `level`, `lessonNumber`, `status`
(`CREATED`, `UPDATED`, `SKIPPED`, `FAILED`), optional `lessonId`, and failure `error`
with code/message/details. Whole-file errors use the ordinary error envelope.

Configuration: `lesson.content.import.max-file-bytes` defaults to 5,242,880 (5 MiB),
allowed range 1–10,485,760; `lesson.content.import.max-items` defaults to 100,
allowed range 1–500. The HTTP multipart body limit also applies, including framing.
File I/O is asynchronous; JSON parsing and validation run on a Vert.x worker and
return to the original context before persistence. Only validated lesson data is
persisted; upload files use Quarkus' temporary upload lifecycle, with no permanent
copy made by the feature.

## Persistence and verification

`lesson_contents` has a shared `lesson_id` primary key, native MySQL JSON,
schema version, application-owned revision and UTC timestamps. There are no JSON
indexes. `LessonContentJsonFormat` isolates database JSON from REST serialization;
the pinned Quarkus version requires a dedicated format mapper when REST Jackson
customizations exist. Native JSON insert/read/replacement and supplementary-plane
Japanese round trips are exercised in `LessonContentMappingMysqlTest`.

Focused tests follow the existing Maven/JWKS conventions. Real MySQL suites remain
opt-in and require a disposable `vocabulary_import_test` database migrated through
V12. No live authentication service is needed.
