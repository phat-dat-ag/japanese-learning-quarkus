# Kanji Quiz Player History (Phase 5.1)

Both endpoints require the case-sensitive User or Admin role and the exact verified
JWT `sub` (nonblank, at most 255 Unicode code points). Admin has no ownership bypass.
Responses use the existing `ApiResponse` data/meta and centralized error envelope.

## List

`GET /api/v1/kanji-quiz/history?page=0&size=20`

```json
{"items":[{"sessionId":42,"status":"COMPLETED","levelId":null,"lessonId":null,
"completedAt":"2026-10-09T10:15:30.123456","questionCount":10,
"correctCount":7,"incorrectCount":3,"score":7}],
"page":0,"size":20,"totalElements":1,"totalPages":1}
```

Only owned COMPLETED sessions appear. Sort order is completedAt DESC, sessionId DESC.
Page is zero-based (default 0), size is 1-100 (default 20), and page*size must fit
an int offset. Empty/out-of-range pages return 200 and an empty items list with
accurate totals; totalPages is 0 for an empty history. Separate offset-page requests
can shift when new sessions complete; each response has a consistent transaction view.

`questionCount` is the total, score equals correctCount, and incorrectCount is the
remainder. The Phase 1-4 schema does not persist original creation filters, so levelId
and lessonId are explicitly null. They are not inferred from mutable classifications.
No migration or change to session creation is introduced.

## Detail

`GET /api/v1/kanji-quiz/history/{sessionId}`

`data.session` uses the same summary shape as a list item. `data.questions` is ordered
by the persisted questionNumber. Each question contains sessionQuestionId,
questionNumber, sentenceReading, targetStart/targetLength (Unicode code points),
options (`id`, `text`), selectedOptionId, correctOptionId, correct, nullable
explanationVi/explanationEn, and answeredAt. Option IDs ascend to preserve the
original stored shuffle. IDs refer to session snapshots. Times use the existing
UTC LocalDateTime serialization without offsets.

All content comes from session/question/option/answer snapshots, never mutable
bank, vocabulary, example or classification tables. Missing, foreign, IN_PROGRESS
and ABANDONED sessions return identical 404 QUIZ_SESSION_NOT_FOUND responses. Even
fully answered sessions are unavailable until explicitly completed. Existing Phase 4
GET endpoints remain unchanged and never expose answer keys.

## Errors and read-only guarantees

- 400 QUIZ_HISTORY_INVALID: negative page, size outside 1-100, or overflowing page*size,
  with field validation details. Nonpositive detail IDs return 400 BAD_REQUEST.
- 401/403: existing authentication, unusable-subject and role errors.
- 404 NOT_FOUND: malformed/overflowing integer path or query parameters under the
  existing REST conversion convention. Valid missing/foreign/incomplete detail IDs
  return QUIZ_SESSION_NOT_FOUND.
- 500: unexpected errors use the existing sanitized response.

GETs run in reactive transactions with no write locks or mutations. List performs a
count, a bounded session-page query, and one grouped answer-count query for that page
(skipped for an empty page). Detail performs four bulk queries regardless of question
count. History never writes sessions, answers, scores or user progress.

Focused verification: QuizHistoryMysqlTest and QuizHistoryOpenApiTest, plus shared
merged OpenAPI contracts. Comprehensive regression is deferred to Phase 5.3.
Progress/statistics endpoints and UI remain outside Phase 5.1.
