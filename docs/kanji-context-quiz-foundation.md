# Kanji in Context Quiz: Phase 1 foundation

## Ownership and schema

V6 adds only Quiz tables; it uses no triggers or stored routines. No sample questions, existing-row updates,
existing-table alterations, or changes to V1-V5. MySQL 8.4 is the existing target.

| Table | Purpose and relationships |
| --- | --- |
| `quiz_questions` | One bank for EXAMPLE/CUSTOM; optional vocabulary FK; EXAMPLE FK to `example_sentences`; CUSTOM-owned reading; target, explanations, status, optimistic version and UTC timestamps |
| `quiz_question_options` | Unordered option text and correctness; question FK; unique text and at most one correct option |
| `quiz_question_levels` | Explicit many-to-many question/JLPT classification |
| `quiz_question_lessons` | Explicit many-to-many question/lesson classification |
| `quiz_sessions` | Case-sensitive JWT subject, lifecycle, expected question count, version and timestamps |
| `quiz_session_questions` | Immutable sentence/target/explanation/source/version snapshot, unique question position per session |
| `quiz_session_options` | Immutable option text/correctness independent of bank option IDs; no display order |
| `quiz_answers` | One immutable answer per session question; composite FK requires the selected option to belong to that snapshot |
| `quiz_user_progress` | Unique subject/question, attempts, correct count, last answer, optimistic version |

JWT `sub` is stored exactly (case-sensitive, up to 255 code points); no User Service FK.
Neither bank nor snapshot options have a display-order field. Phase 2 must randomize
presentation and accept stable snapshot-option IDs, never option positions.

Existing examples are shared through `vocabulary_examples`; vocabulary levels and
lessons are also many-to-many. Quiz classification is deliberately explicit rather
than inheriting potentially ambiguous tags from every linked vocabulary. Both sources
use the same classification tables. A tagged lesson implies its current JLPT level.
When both level and lesson filters are supplied, the lesson must belong to that level.
A question may be classified under multiple levels/lessons, or none (unfiltered only).

## Source lifecycle

EXAMPLE questions never store or write the source reading in the bank. Publication
locks the example and fingerprints its reading plus `updated_at`. Candidate selection
excludes a mismatched fingerprint or missing example. Reverting the source content
does not automatically restore eligibility because its timestamp changed. Changes to
other example fields conservatively require revalidation too. No Vocabulary API change
or trigger on a Vocabulary table is needed.

Deleting an example sets its question reference to NULL, keeping the bank question and
history. Its editorial status may still read PUBLISHED, but it is ineligible for new
games until an editor supplies and validates an existing example. It can instead be
archived. Deleting an optional vocabulary reference simply detaches it. For EXAMPLE
publication, an optional vocabulary reference must have an existing example assignment.

CUSTOM questions own `sentenceReading`, cannot reference an example, and need neither
vocabulary nor classifications. Source readings are not normalized or silently rewritten.

`prepareForEdit` locks the question, demotes it to DRAFT, clears source validation and
flushes before content/options are edited. All supported edit commands must use this boundary before changing a question or its options. Editing drafts and republishing increments the optimistic version.
Archival prevents new games. Existing sessions always grade against snapshots, never the
current question, example, explanation, or option. Snapshot rows/options and answers are mapped `@Immutable`; the domain snapshot is an immutable value.
Direct SQL updates are not guarded and are outside supported application write paths. Deleting a bank question detaches session snapshots
and removes its aggregate progress; it does not remove session answers. Prefer archival.
Deleting a session explicitly cascades its history, for a future retention/account-erasure flow.

## Validation and transaction boundary

`QuestionTarget` uses zero-based Unicode **code points**, not UTF-16 indices or grapheme
clusters. Start is nonnegative, length positive, range must fit, and the selected substring
must exactly equal `targetReading`. Hiragana/Katakana letters, following combining voicing
marks and prolonged sound marks are accepted; Kanji is not a target reading. Surrogates
must be well formed. Half/full-width or composed/decomposed readings are not folded when
matching a source: callers must send the exact substring. Punctuation elsewhere in a reading
is allowed. Supplementary characters count as one; decomposed kana count as multiple points.

`QuizQuestionRules` requires exactly four nonblank options, exactly one correct option,
no surrounding whitespace, and no duplicates after NFKC normalization. Limits are measured
in code points: sentence 1000, target/option 200, each optional VI/EN explanation 2000.
No option is required to be a single Kanji character.

The database enforces source sentence ownership, valid status/ranges, distinct stored
option text, one correct option maximum, answer-option ownership, and progress bounds.
Source-specific example references, exact four/one publication, Unicode-script and NFKC
rules are enforced by the transactional domain service. CHECK constraints cannot count
child rows, and the existing schema-scoped Flyway account cannot create triggers with
binary logging enabled. By deployment policy, **no triggers are used**. All supported
create/import/update paths must use `QuizPublicationService`; direct SQL or repository
mutations can bypass cross-row and immutable-history rules and are not supported APIs.

A future write command should own one `@WithTransaction` spanning draft creation/edit,
options/classifications, validation and publication. Nested publication joins that transaction.
Lock question first, then example; option reads are locking/current reads. Always demote and
flush before option edits. Domain failures roll back the whole command. Do not modify source
sentences through Quiz. Native/bulk writes must preserve optimistic versions or be avoided.

Candidate IDs are paged with level/lesson filters; they are not a reservation.
`QuizSessionSnapshotService.create` accepts the authenticated subject and 1-100 distinct
selected question IDs. It locks all questions in ID order, then all distinct examples in
ID order, and revalidates live fingerprints, publication status, targets and options.
It persists the complete session and snapshots/options in one reactive transaction,
preserving requested question order and enforcing the expected count. Failure leaves
no partial session. Future Player APIs must use this service after candidate selection;
they must obtain the subject from verified JWT claims rather than request payloads.

Source eligibility is checked against live data both during selection and under source
locks during session creation. Existing Vocabulary update/delete operations need no
Quiz callback: changed readings/timestamps or a detached FK invalidate new sessions.
Historical snapshots remain unchanged. No cross-row CHECK can enforce snapshot count,
four options, live fingerprint freshness, or immutable history against arbitrary SQL.
Those guarantees depend on these service boundaries and immutable ORM mappings.

Future answer handling must lock the session by authenticated subject, verify IN_PROGRESS,
insert one answer, and update progress in the same transaction. A repeated answer must be
idempotently returned or rejected using the unique answer key; it must never increment progress
twice. Handle concurrent first-progress creation with the unique subject/question key and retry
the entire transaction. Progress is an aggregate, while immutable answers remain the history.

## Scope and verification

No REST resources, UI, player orchestration, scores, scheduling algorithm, or authorization
policy is added. Existing logging/security/retention configuration and business logging are
unchanged. Future endpoint events should follow existing after-commit logging conventions and
must not log submitted sentences, answer payloads, or tokens.

Focused tests: `QuizQuestionRulesTest` and `QuizFoundationMysqlTest`. The integration test reuses
the existing opt-in `VocabularyMysqlTestProfile` and requires a disposable database named
`vocabulary_import_test`, migrated through V6. Run with:

```powershell
.\mvnw.cmd verify '-Dquarkus.http.test-port=0' '-Dvocabulary.mysql.tests=true' `
  '-Dvocabulary.mysql.url=mysql://127.0.0.1:<port>/vocabulary_import_test' `
  '-Dvocabulary.mysql.password=<test-password>'
```

Apply/validate migrations through the existing external Flyway container. V6 works with
a schema-scoped migration user and does not require SUPER, CREATE TRIGGER, or changes to
binary-log trust configuration. Ordinary application execution needs no DDL. MySQL DDL is
not transactional: inspect and remove only partially created Quiz objects before retrying
a failed initial migration. Never alter an applied production migration.
