# Logging and distributed tracing

## Architecture and migration

The existing JBoss logging, early Vert.x correlation filter, REST mappers,
vocabulary interceptor and off-thread retention are retained. Two official,
BOM-managed Quarkus 3.38.2 extensions are added: `quarkus-logging-json` and
`quarkus-opentelemetry`. There is no alternate logging backend, new service,
blocking database instrumentation or schema change.

The JSON extension owns escaping and NDJSON framing. `ApplicationJsonFields`
uses its `JsonProvider` SPI to export allowlisted MDC fields with numeric types.
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
| `outcome` | `success`, `failure`, or vocabulary batch `partial_failure` |
| `error.code`, `error.type` | Safe code and exception class or HTTP/security category |
| `error.stack_trace` | Bounded sanitized cause classes and code locations |
| `operation`, `reason` | Stable operation and rejection category |
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
`http`, `authentication` or `authorization`.

No JWTs, raw headers, subjects, whole entities or request/response bodies are logged
by the new instrumentation. Preserve `OidcProvider=ERROR` because lower-level
verification messages can expose tokens. Application redaction is not a universal
sanitizer for third-party libraries or independently configured exporters.

## Business and database visibility

Vocabulary imports, lesson writes/batches and Admin edits retain
`LogVocabularyOperation`, now with typed counts/IDs/durations and
`<operation>.completed` / `.rejected` events. Nested lessons emit one batch
summary. JLPT catalogue counts are structured DEBUG diagnostics.

`LogOperation` marks flashcard session creation/finish/abandon, bookmarks,
statistics, quiz session creation/completion, answers, progress and question
Admin operations. Success follows transaction completion. Conflicts use WARN;
ordinary validation rejection uses DEBUG. Quiz imports suppress nested item logs.

Reviews emit `flashcard.review.accepted` or `flashcard.review.replayed` after
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

Exactly one incoming `X-Correlation-ID` is accepted if it contains 1?64 ASCII
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
| `APP_SERVICE_NAME` | `backend` |
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
| `APP_OTEL_SAMPLE_RATIO` | `0.1`, range 0?1 |
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

## Verification

Tests parse real HTTP/file logs as JSON and cover schema/types, correlation
validation, W3C extraction/outbound propagation, concurrent reactive isolation,
success, validation/deserialization, conflicts, unexpected errors, 401/403,
unknown routes, one terminal event, secret sentinels, safe stacks, transaction
completion, compressed rotation/reopen and archive cleanup.

```powershell
.\mvnw.cmd test "-Dtest=ApplicationFileLoggingTest,CorrelationIdTest,CorrelationIdValidationTest,ProductionErrorTest,OperationLogTest,VocabularyOperationLogTest,LogRetentionTest" "-Dquarkus.http.test-port=0"
.\mvnw.cmd verify "-Dquarkus.http.test-port=0"
docker build --check .
```

Quarkus 3.38.2 currently discovers the existing guarded MySQL test profiles before
their disabled-test condition. If default verification fails with
"dedicated vocabulary_import_test database", supply
`-Dvocabulary.mysql.url=mysql://127.0.0.1:1/vocabulary_import_test` without enabling
`vocabulary.mysql.tests`. This satisfies discovery and does not connect to MySQL.
This pre-existing profile behavior was not changed by the logging enhancement.

Opt-in MySQL checks remain documented in README. Rotation/reopen tests exercise
the real file handler; they do not prove external collector, production volume
or .NET tracing integration behavior. Validate those during deployment.

### Container verification performed

The root source-built image was run with a new isolated bind directory and an
8 KiB rotation threshold. Forty unauthorized API requests were sent across a
container stop/start. All 140 stdout and file records parsed as JSON, twelve
gzip archives were readable, and each request had one terminal event and
consistent trace/span IDs. A query-string secret sentinel was absent. The four
launcher stderr lines (two starts) were separate from application NDJSON.
The task-created container was removed; existing containers/volumes were untouched.
This verifies local restart/rotation behavior, not production collector delivery.

### Final automated results

- Offline `verify` passed: 255 executed tests; 135 opt-in test cases were skipped.
  The documented dedicated-database URL discovery workaround was supplied.
- All opt-in MySQL checks passed on a new MySQL 8.4 tmpfs database after V1?V11:
  203 `*MysqlTest` cases plus 12 `VocabularyMysqlImportTest` cases.
- Quarkus packaging, root Docker source build, Docker BuildKit checks and
  `git diff --check` passed. Database containers were removed without volumes.
- No OTLP collector delivery, production collector configuration, native build
  or real .NET-issued access-token end-to-end verification is claimed.

### Representative test log excerpts

The following are parsed output excerpts, in order: successful request,
validation failure, authentication failure, business conflict, unexpected server
failure and slow request. Other formatter fields are omitted here for readability.

```json
{"@timestamp":"2026-10-26T08:24:22.415Z","level":"WARN","service.name":"backend","event.name":"http.request.completed","traceId":"0123456789abcdef0123456789abcdef","spanId":"f7bd9380a1825ae5","correlationId":"file-2e6ead63-104f-48fb-8681-7184b6941df0","http.route":"/correlation-probe","http.response.status_code":200,"durationMs":34,"outcome":"success"}
{"@timestamp":"2026-10-26T08:24:22.528Z","level":"WARN","service.name":"backend","event.name":"http.request.failed","traceId":"ee869db51c4022966b43f24b4427bb6a","spanId":"a02c1af53043cc52","correlationId":"file-2e6ead63-104f-48fb-8681-7184b6941df0-validation","http.route":"/error-probe","http.response.status_code":400,"durationMs":27,"outcome":"failure","error.type":"io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationException","error.code":"BAD_REQUEST"}
{"@timestamp":"2026-10-26T08:24:22.571Z","level":"WARN","service.name":"backend","event.name":"http.request.failed","traceId":"c7f1b0c3ddb341313d5164fd82e6df3b","spanId":"865a3d9227ba9901","correlationId":"file-2e6ead63-104f-48fb-8681-7184b6941df0-rejected","http.route":"/api/v1/jlpt-levels/*","http.response.status_code":401,"durationMs":7,"outcome":"failure","error.type":"authentication","error.code":"UNAUTHORIZED"}
{"@timestamp":"2026-10-26T08:24:22.492Z","level":"WARN","service.name":"backend","event.name":"http.request.failed","traceId":"9643183314a486b3399e10c2a6ce3a9b","spanId":"72e4b37bd7c90f70","correlationId":"file-2e6ead63-104f-48fb-8681-7184b6941df0-conflict","http.route":"/error-probe/{kind}","http.response.status_code":409,"durationMs":2,"outcome":"failure","error.type":"com.japaneselearning.common.exception.ConflictException","error.code":"TEST_CONFLICT"}
{"@timestamp":"2026-10-26T08:24:22.466Z","level":"ERROR","service.name":"backend","event.name":"http.request.failed","traceId":"2e8130c8302b86a0e41467a40596cb72","spanId":"aceba760b3c70279","correlationId":"file-2e6ead63-104f-48fb-8681-7184b6941df0-error","http.route":"/error-probe/{kind}","http.response.status_code":500,"durationMs":25,"outcome":"failure","error.type":"java.lang.IllegalStateException","error.code":"INTERNAL_SERVER_ERROR"}
{"@timestamp":"2026-10-26T08:24:22.415Z","level":"WARN","service.name":"backend","event.name":"http.request.slow","traceId":"0123456789abcdef0123456789abcdef","spanId":"f7bd9380a1825ae5","correlationId":"file-2e6ead63-104f-48fb-8681-7184b6941df0","http.route":"/correlation-probe","http.response.status_code":200,"durationMs":34,"outcome":"success"}
```
