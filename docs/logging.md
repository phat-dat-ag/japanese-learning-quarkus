# Logging and distributed tracing

## Architecture and migration

The existing JBoss logging, early Vert.x correlation filter, REST mappers,
vocabulary interceptor and off-thread retention are retained. Two official,
BOM-managed Quarkus 3.38.2 extensions are added: `quarkus-logging-json` and
`quarkus-opentelemetry`. There is no alternate logging backend, new service,
blocking database instrumentation or schema change.

The JSON extension owns escaping and NDJSON framing. `ApplicationJsonFields`
uses its `JsonProvider` SPI to export allowlisted MDC fields with numeric types. Invalid or overflowing integer values are omitted without dropping the record or changing the field to a string.
`StructuredLog` attaches event fields temporarily and restores prior MDC.
Quarkus snapshots queued log context and propagates it through duplicated
Vert.x contexts. OpenTelemetry owns tracing; the early filter owns HTTP lifecycle
events; business interceptors observe reactive transaction completion.

Previously, completion was DEBUG-only, business fields were embedded in text,
and REST generated unrelated trace IDs. Migration changes:

- The default active filename is **application.json.log**. Existing
  `application.log` and its archives are not renamed or deleted. Point collectors
  at the new stable file; retain legacy files under a separately approved policy.
  Do not override the filename to an existing text file if every line must be JSON.
- Existing **X-Trace-Id echo behavior is preserved** for REST requests. A valid
  supplied value remains the response `X-Trace-Id` and `meta.traceId` alias;
  otherwise they use the canonical trace. The alias is logged as `legacyTraceId`
  and never controls OpenTelemetry. `X-Distributed-Trace-Id` always returns the
  actual W3C trace ID, also named `traceId` in logs. Use `traceparent` to continue
  a distributed trace. Envelopes, error codes, challenges and authorization remain
  unchanged. Early proactive-authentication failures use the canonical trace.
- Keep `X-Correlation-ID` for diagnostic identifiers. It is independent of the
  trace and never an identity or authorization credential.
- Disable export with the exporter switch, not by disabling the SDK.

## JSON schema

One UTF-8 JSON object per event, without pretty printing. Newlines and stack text
are escaped inside the object. Dotted keys are literal top-level keys.

| Field | Type / meaning |
| --- | --- |
| `@timestamp` | ISO-8601 UTC string, millisecond precision |
| `level`, `logger.name`, `thread.name`, `message` | Strings |
| `service.name`, `service.version`, `deployment.environment` | Service metadata |
| `event.name` | Stable application event name, when applicable |
| `traceId`, `spanId`, `correlationId` | Strings for request-associated logs |
| `legacyTraceId` | Optional validated legacy response alias, independent of W3C tracing |
| `http.request.method` | Allowlisted method or `OTHER` |
| `http.route` | Static matched template; safe family before matching; `/unmatched` otherwise |
| `http.response.status_code`, `durationMs` | Integers; duration uses a monotonic clock |
| `outcome` | `success`, `failure`, batch `partial_failure`, or vocabulary `cancelled` |
| `error.code`, `error.type` | Safe code and exception class or HTTP/security category |
| `error.stack_trace` | Bounded sanitized cause classes and code locations |
| `operation`, `reason` | Stable operation and decision/rejection category |
| `learning.status`, `learning.previous_status` | Resulting/prior SRS state names; never the learner subject |
| `intervalSeconds`, `repetitions`, `lapses`, `page`, `size` | Integer scheduling/pagination diagnostics |
| `dueCount`, `studiedCount`, `masteredCount` | Integer statistics counts from the existing response |
| `studiedBasisPoints`, `masteryBasisPoints` | Integer hundredths of a percentage point: 3333 means 33.33% |
| `sessionId`, `itemId`, `vocabularyId`, `lessonId`, other allowlisted IDs | Integers, never subjects |
| `count`, `total`, `created`, `updated`, `succeeded`, `failed` | Integer summary metrics |

Startup/framework records need not have application events or request context.
The formatter also includes sequence/process metadata. No redundant request ID
is created. Child spans can differ within a trace; HTTP start and terminal
events retain the server span captured at arrival.

## Lifecycle, errors and redaction

`CorrelationIdFilter` runs before proactive authentication, validates IDs,
captures the server span/start time and registers headers and an end handler.
`RequestRouteFilter` records static route templates after matching. Raw paths and
query strings never become arbitrary event fields.

- `http.request.started`: INFO, safe route family, method and identifiers.
- `http.request.completed`: INFO, status/duration/outcome; WARN when slow.
- `http.request.failed`: WARN for expected HTTP errors, ERROR for 5xx.
- `http.request.slow`: WARN performance signal, not another terminal event.

This includes missing/invalid tokens, forbidden roles, binding/deserialization,
bean validation, business exceptions, unmatched routes and asynchronous work.
`/q/` operational routes return IDs but omit lifecycle logs to avoid probe noise.
HTTP parser rejections before application routing are outside this filter.

Mappers attach exception categories; the response filter records the existing
safe code. Unexpected failures emit one `application.exception` diagnostic with
up to five cause classes and twelve code locations per cause. Exception messages,
source paths, SQL and payloads are omitted. The HTTP terminal event has no second
stack. Batch item errors retain central diagnostics. Propagated database/
connectivity failures use this path; existing optimistic/unique conflict
translations retain their 409 codes. Unknown framework throwable categories are
`http`, `authentication` or `authorization`. Interrupted responses use `transport`
and the `REQUEST_INTERRUPTED` fallback code when no error status is available.

No JWTs, raw headers, subjects, whole entities or request/response bodies are logged
by the new instrumentation. Preserve `OidcProvider=ERROR` because lower-level
verification messages can expose tokens. Application redaction is not a universal
sanitizer for third-party libraries or independently configured exporters.

## Business and database visibility

Vocabulary imports, lesson writes/batches and Admin edits retain
`LogVocabularyOperation`, now with typed counts/IDs/durations and
`<operation>.completed` / `.rejected` events. Nested lessons emit one batch
summary. JLPT catalogue counts are structured INFO summaries.

`LogOperation` marks flashcard session creation/finish/abandon, bookmarks,
statistics, quiz session creation/completion, answers, progress and question
Admin operations. Success follows transaction completion. Statistics/progress reads complete at INFO. Described business rejections use WARN;
legacy undescribed validation retains its DEBUG default. Quiz imports suppress nested item logs and emit a WARN `quiz.question.import.partial_failure` summary when any items fail, with `total`, `succeeded`, and `failed` counts.

Reviews emit INFO `flashcard.review.accepted` or DEBUG `flashcard.review.replayed` after
commit, using subscription-local outcome state. `flashcard.review.rejected`
includes session/item IDs and the existing code, including `FLASHCARD_NOT_DUE`.
The logging does not modify persistence or response behavior.

Operation starts are DEBUG; significant successes INFO. `<operation>.slow` is
WARN when the configured threshold is met, including failure/cancellation.
Durations include reactive database work and transaction completion; they are
not individual SQL timings. There are no blocking timers/waits or parallel
operations on a shared reactive session.

OpenTelemetry instruments supported managed Vert.x HTTP and reactive SQL clients.
Use sampled child spans for query-level investigation. SQL/bind logging remains
disabled; repositories do not log every query. The raw Hibernate
`org.hibernate.orm.jdbc.error` category is OFF because database error text may
include duplicate-key values/subjects; central sanitized diagnostics and business
rejections remain enabled. Collector access controls and
attribute retention apply separately to exported telemetry.

## Correlation and W3C context

Exactly one incoming `X-Correlation-ID` is accepted if it contains 1-64 ASCII
letters, digits, underscore or hyphen. Missing, blank, oversized, duplicate,
comma-joined or malformed values are replaced with a UUID without hyphens.
The chosen value is returned even on early authentication failures.

OpenTelemetry validates/extracts `traceparent`, generates missing/invalid context
and propagates across reactive execution. Managed outbound HTTP clients inject
W3C headers. A future business client must explicitly forward the validated
correlation header if its peer needs it. No application HTTP forwarding clients
exist here; JWKS retrieval remains owned by OIDC. .NET/Gateway must send valid
W3C headers to join the trace. Do not add a second tracing SDK/agent.

Sampling is parent-based with a 10% root ratio; parent decisions are respected.
Unsampled requests still have IDs in logs. Export is disabled by default so no
collector is required. The incoming span is the parent, not the server span.

## Configuration

| Environment variable | Default / use |
| --- | --- |
| `APP_SERVICE_NAME` | `quarkus-service` |
| `APP_ENVIRONMENT` | `production`; `development` in dev and `test` in tests |
| `ROOT_LOG_LEVEL`, `APP_LOG_LEVEL` | `INFO`; latter applies to application packages |
| `APP_LOG_CONSOLE_JSON`, `APP_LOG_FILE_JSON` | `true` |
| `APP_LOG_FILE_ENABLED` | `true`; false in normal test profile |
| `APP_LOG_DIR` | `logs`; root image uses `/deployments/data/logs` |
| `APP_LOG_FILE` | `application.json.log` |
| `APP_LOG_ROTATION_SIZE` | `50M` |
| `APP_LOG_MAX_BACKUPS` | `100` size backups per date; `0` disables size rotation |
| `APP_LOG_RETENTION_DAYS` | `30`, positive |
| `APP_SLOW_OPERATION_MS` | `1000`; `0` emits every slow signal for diagnostics/tests |
| `APP_OTEL_EXPORT_ENABLED` | `false`; tracing context remains active |
| `APP_OTEL_SAMPLE_RATIO` | `0.1`, range 0-1 |
| `QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT` | Set to your collector when enabling export |
| `QUARKUS_OTEL_EXPORTER_OTLP_PROTOCOL` | Standard Quarkus setting; match the collector |

Production: set service/environment, a persistent writable log directory and
appropriate storage limits. For export set `APP_OTEL_EXPORT_ENABLED=true` and the
collector endpoint. Configure TLS/auth with standard Quarkus OTLP settings and
secret management. No credentials/endpoints are hardcoded. Ratio and export
enablement are runtime settings; build-time instrumentation changes need rebuilds.

Temporarily set `APP_LOG_LEVEL=DEBUG`, then restore INFO. Keep the OIDC override.
Avoid root TRACE, SQL binds and payload logging for routine diagnosis.
References: [Quarkus JSON logging](https://quarkus.io/guides/logging/) and
[OpenTelemetry](https://quarkus.io/guides/opentelemetry/). Tests use pinned 3.38.2.

## Files, rotation and Docker

The active path is stable and appends on restart; rotate-on-boot is disabled.
Daily/size archives resemble `application.json.log.2026-10-26.1.gz` and
`application.json.log.2026-10-26.gz`. Compression runs on the async handler.
Size thresholds are checked between records; one large event can exceed the
threshold. This is not a strict compressed-byte limit.

Retention checks at startup and hourly, removing only matching regular
daily/numbered/compressed archives with modification times strictly older than
the cutoff. It never follows symlinks, recurses, deletes the active file or matches
another prefix. Uncompressed daily archives for the configured prefix remain
recognized. Backup caps may evict same-day archives before 30 days. There is no
aggregate disk quota: monitor capacity and choose limits for traffic.

The existing Docker volume persists logs across replacement. UID 185 needs write
access; the root image seeds mode 0750. Use separate directories per replica.
There is no Compose file here; no volumes are recreated/removed. Retention resumes
after startup. Docker stdout retention is separate. The image's shell launcher emits two text
diagnostics to stderr on each start; those are outside application logging.
Application stdout and files are NDJSON. Configure collectors to distinguish
streams instead of treating launcher stderr as application JSON.

Both handlers retain 8192-record discard-on-overflow queues to protect reactive
threads. Overload/abrupt termination can lose records: these are diagnostics, not
a durable audit trail. Collect console NDJSON or tail the stable active file with
rotation-aware offsets; avoid collecting both without deduplication.

## Troubleshooting

1. Send `X-Correlation-ID: support-42`; optionally send
   `traceparent: 00-0123456789abcdef0123456789abcdef-0123456789abcdef-01`.
2. Record `X-Correlation-ID` and `X-Distributed-Trace-Id`, including on errors.
   Existing clients can search `legacyTraceId` when they supply `X-Trace-Id`.
3. Search active/archived logs:

   ```sh
   jq -c 'select(.correlationId == "support-42")' logs/application.json.log
   jq -c 'select(.traceId == "0123456789abcdef0123456789abcdef")' logs/application.json.log
   gzip -cd logs/application.json.log.2026-10-26.1.gz | jq -c 'select(.correlationId == "support-42")'
   ```

4. Follow start, business events and one terminal event. Use error codes for
   expected failures, `application.exception` for unexpected diagnostics and
   `.slow` for latency.
5. Find the trace in your collector if sampled/exported; inspect database/client
   spans. A missing exported trace does not imply missing request IDs.

Illustrative sanitized excerpts; normal output also includes service/logger/thread
metadata:

```json
{"@timestamp":"2026-10-26T07:00:00.000Z","level":"INFO","event.name":"http.request.started","correlationId":"support-42","traceId":"0123456789abcdef0123456789abcdef","spanId":"1111111111111111","http.request.method":"POST","http.route":"/api/v1/flashcards/*"}
{"@timestamp":"2026-10-26T07:00:00.030Z","level":"INFO","event.name":"flashcard.review.replayed","correlationId":"support-42","traceId":"0123456789abcdef0123456789abcdef","spanId":"1111111111111111","sessionId":42,"itemId":7,"durationMs":28,"outcome":"success"}
{"@timestamp":"2026-10-26T07:00:00.032Z","level":"INFO","event.name":"http.request.completed","correlationId":"support-42","traceId":"0123456789abcdef0123456789abcdef","spanId":"1111111111111111","http.response.status_code":200,"durationMs":32,"outcome":"success"}
```

| Scenario | Terminal event / level | Status / code |
| --- | --- | --- |
| Validation | `http.request.failed` / WARN | 400 / `BAD_REQUEST` |
| Authentication | `http.request.failed` / WARN | 401 / `UNAUTHORIZED` |
| Business conflict | `http.request.failed` / WARN | 409 / feature code |
| Unexpected error | `http.request.failed` / ERROR, plus one stack diagnostic | 500 / `INTERNAL_SERVER_ERROR` |
| Slow success | `http.request.completed` plus `http.request.slow` / WARN | 200, numeric duration |

## Developer conventions and workflow coverage

Use `@LogOperation` on a meaningful CDI service boundary, outside the reactive
transaction interceptor. Production annotated methods return `Uni`; synchronous
results and exceptions are also supported. Do not apply it to a `Multi`, future,
or publisher without adding lifecycle support and tests. Internal `Multi` import
pipelines are collected into `Uni` before returning. Method bindings override
class bindings; metadata is nonbinding. Avoid annotating every repository or
helper, and prefer method annotations when a class has several operations.

`value` is a static operation name. `id` only renames a `Long` argument literally
named `id`, for example `@LogOperation(value = "quiz.session.finish", id = "sessionId")`.
Arguments already named `sessionId`, `itemId`, `vocabularyId`, `lessonId`, `levelId`
or `questionId` are recognized directly. The empty default deliberately omits an
ambiguous `id`; writing `id = ""` explicitly adds nothing. No DTO, subject, arbitrary
argument, or return value is inspected or stringified. Vocabulary operations keep
the existing, separately tested `LogVocabularyOperation` binding.

`OperationLog.outcome` carries explicit safe fields in subscription context until
outer completion, including commit. Session creation supplies the generated ID
and selected count this way. A rollback never emits those success fields. Context
markers are removed after success, failure, or cancellation; each subscription
starts fresh. Nested operations share the outer summary. `StructuredLog.event`
restores previous MDC values and skips disabled events before stringifying fields.
Neither interceptor leaves an operation name in ambient MDC after failure.

| Level | Developer rule |
| --- | --- |
| INFO | Successful meaningful writes and batch start/completion; HTTP lifecycle. Use `log.info()` for safe static operational notices and `StructuredLog.event` when IDs/counts matter. |
| WARN | Conflicts, partial batches, slow work and recoverable operational failures. HTTP 4xx is logged once by the lifecycle owner; avoid another warning for every validation layer or normal empty result. |
| DEBUG | Selection, normalization, scheduling and validation stages; routine read completion (`level = Logger.Level.DEBUG`). These decisions can precede rollback and are not commit evidence. |
| ERROR | Unexpected technical failure: the central mapper emits one sanitized stack diagnostic. Do not add `log.error(failure)` at each service/repository boundary or log raw SQL/exception messages. |

For `log.warn()` and `log.debug()`, use only static messages or explicitly safe
scalar values. For business events prefer typed fields, as in the actual review
path:

```java
StructuredLog.event(LOG, Logger.Level.DEBUG, "flashcard.review.mode",
        "Selected the review scheduling mode; progress has not yet been committed",
        Map.of("sessionId", session.id, "itemId", item.id, "vocabularyId", item.vocabularyId,
                "reason", scheduling ? "scheduling" : "practice"));
```

Names use feature, business operation and phase separated by dots; new multiword
phases use underscores. Existing event names remain stable, including older
`updateOption`/`correctOption` names. Never embed IDs or caller text in event names.
Keep field types consistent and add a provider allowlist entry plus a JSON test
when introducing a new field. Do not put subjects, file paths/names, tokens,
question/answer text, query strings, headers or bodies into logging fields.

All REST resources and their service/repository paths were included in the audit.
The common HTTP lifecycle supplies start, matched route, status, duration and
trace/correlation context for every application API, including read-only paths
that do not need additional INFO business events.

| Module / workflows | Business visibility and persistence boundary |
| --- | --- |
| Vocabulary JSON/file imports | INFO start and post-transaction counts; DEBUG structure/assignment validation; WARN rejection. Sequential relation importers remain under one atomic transaction, without per-row INFO logs. |
| JLPT and lessons | Catalogue/lookup diagnostics, lesson create/update IDs, batch success/failure counts. Existing uniqueness and level checks propagate safe codes; batch item technical failures use the central diagnostic helper. |
| Vocabulary Admin / Kanji | Core, reading, meaning, pitch accent, example, Kanji and assignment writes retain their operation events. Shared Kanji metadata/readings and example content checks add DEBUG IDs/counts before conflict decisions. |
| Flashcard catalogue and personal collections | Existing bounded list/detail diagnostics; DEBUG due/bookmark/forgotten collection counts and pagination. Personal enrichment uses bulk progress/bookmark lookups; no subject or card text is logged. |
| Flashcard learning / SRS | Session selection mode/count/limit; generated session IDs after commit; review scheduling/practice decisions; DEBUG rating category, interval and resulting state. Accepted/replayed reviews, not-due/version conflicts, finish/abandon and bookmarks retain operation outcomes. Bookmark completion confirms requested state, not necessarily a new row. |
| Quiz Admin / imports | Authoring/publication/archive operations, generated question IDs, DEBUG custom/example source selection; batch start and WARN partial summaries. Per-item transactions remain independent. |
| Quiz gameplay / snapshots | DEBUG eligibility and locked-bank revalidation; generated session IDs/counts after commit; answer and completion events, ownership/ordering/conflict codes. Snapshot and classification persistence remain sequential and immutable. |
| Quiz history / progress | HTTP route and lifecycle for history/detail/config/next reads; progress completion at INFO. Normal empty history/next results are not warnings. |
| Repositories and external operations | Reactive persistence is represented by enclosing operation duration/outcome and sampled SQL spans. No per-query INFO logging or bind values. No business HTTP client exists; OIDC owns JWKS retrieval. |
| Security and common infrastructure | Early 401/403, validation, deserialization and central errors share request tracing. Health probe noise stays excluded. Retention failure retries remain WARN and file I/O stays on its worker. |

For an actual API timeline, call `POST /api/v1/flashcards/sessions/{id}/items/{itemId}/review`
with `X-Correlation-ID: support-42` and a valid bearer token, then search that ID
as shown above. At DEBUG, scheduling produces `flashcard.review.mode` and
`flashcard.srs.scheduled`; commit produces `flashcard.review.accepted`, or an
idempotent repeat produces `flashcard.review.replayed`. A not-due conflict instead
produces `flashcard.review.rejected` with `FLASHCARD_NOT_DUE`, followed by exactly
one `http.request.failed`. A normal completion ends with `http.request.completed`.
The optional `.slow` event is a performance signal, not another terminal event.

For storage planning, the defaults permit roughly 101 files of up to 50 MiB per
date before compression, across retained dates (about 148 GiB for 30 dates).
This is only a planning estimate: records can overshoot the size threshold,
cleanup is hourly, and compression ratios vary. Enforce a deployment storage
quota and monitor free space; day retention and per-date backup count do not
provide a strict aggregate byte cap. A full disk or discarded async queue can
lose diagnostics. Launcher/JVM stderr and OTLP delivery still require separate
collector configuration and deployment validation.
## Final audit and verification

All application StructuredLog call sites use descriptive messages; the legacy
four-argument overload remains available for compatibility. Event names remain
stable. HTTP start messages use safe route families before REST matching, while
terminal messages prefer matched templates and include method, status and elapsed
milliseconds. Neither message path includes query strings or dynamic path values.

The five-argument OperationLog.outcome(result, eventSuffix, message, fields, level)
overload allows explicit completion levels. A null level inherits the annotation;
partial_failure retains WARN. Replay/no-op status is never inferred from prose.
Only the explicit flashcard review replay path opts into DEBUG in the final audit;
other meaningful confirmations retain their existing levels. Nested operations
share the enclosing summary; each independent subscription represents another
execution. Do not add retries or cache subscriptions solely for logging.

SRS diagnostics expose the already-computed prior/resulting states and lapse count.
Committed review summaries also expose lapses. Statistics expose due/studied/mastered
counts and fixed-point progress values without additional queries or recalculation
of business results. Existing count/total/reason fields keep their meanings;
example-import skipped counts remain in count with reason=duplicate_examples.
Vocabulary cancellation now emits a DEBUG cancelled event before clearing its
subscription scope. Cancellation is not a committed-success assertion.

No redundant production completion/stack owner or unused compatibility helper was
confirmed. HTTP completion, business completion, DEBUG rule decisions and optional
slow signals are distinct events. Mutation success logs remain after successful transaction
completion; failed commits emit no success, and expected rejections never carry raw stacks.
The final audit removes stale level documentation and duplicate historical
verification narratives rather than removing useful diagnostics.

### Known limitations

- Quarkus 3.38.2 JsonLogGenerator has no top-level decimal overload, and its boolean
  overload delegates to String.valueOf. A focused formatter test detected this.
  No boolean field is exported as a misleading string. Bookmark state remains
  represented by the existing reason/message; typed top-level boolean support is
  deferred to a compatible formatter version. Progress is exported as explicitly
  named integer basis points. The allowlist never exports arbitrary MDC.
- Async discard queues, abrupt termination and disk exhaustion can lose records.
  No aggregate disk quota or durable audit guarantee is provided. Verify collector
  retention and avoid duplicate ingestion of console and file output.
- The pinned Quarkus/OpenTelemetry combination emits an API-usage warning during
  startup/GC metrics outside an active span. Prior diagnosis identified the
  OpenTelemetry exemplar sampler; this audit does not suppress the warning or
  upgrade dependencies. Request tracing is covered separately by tests.
- HTTP parser rejections before routing and image-launcher stderr are outside the
  application lifecycle/NDJSON contract. Real collector delivery, production load,
  native builds and real .NET-issued token integration require deployment checks.

Focused tests cover HTTP/error JSON, correlation/W3C propagation, safe messages,
strict duplicate-key parsing, numeric types, unsupported-field exclusion, reactive
completion/rollback/cancellation, MDC restoration, business outcomes and retention.
Full verification and opt-in MySQL tests use the README commands and a disposable,
externally migrated database. Results below describe this audit only.

### Final audit results (2026-10-28)

- Focused common/Quiz/Vocabulary/Flashcard logging suite: 81 passed, 0 failed,
  0 errors, 0 skipped. An initial run had 80 passes and one new boolean-type
  assertion failure; the incompatible proposed export was removed before the
  passing run. No dependency workaround or schema misrepresentation was retained.
- Full Maven verify with MySQL enabled: 530 passed, 0 failed, 0 errors, 0 skipped;
  includes 215 tests across 15 MySQL suites. Tests used disposable MySQL 8.4,
  externally migrated through V11, including idempotency, concurrent updates,
  rollback, session lifecycle and atomic vocabulary imports. Packaging passed.
- Strict parsing of 4,041 emitted console JSON records found no duplicate keys.
  All 1,297 real HTTP request groups had one start and one terminal event; no
  business/HTTP messages merely repeated their event name. New numeric fields
  retained JSON integer types; checked credential/payload/subject/file/SQL sentinel
  values were absent. The async file test separately validates real file output.
- Rotation/reopen/gzip and bounded retention tests passed. Dockerfile validation
  passed with no warnings; Docker hosted the isolated MySQL tests. A new application
  image build/runtime smoke test was not run in this phase.
- API contracts, schema, security, business rules, SRS calculations and transaction
  boundaries were unchanged. No logging dependency/configuration changes, new
  database queries or blocking request operations were introduced.
