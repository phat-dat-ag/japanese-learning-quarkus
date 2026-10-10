-- Existing questions retain NULL until edited through Admin APIs. No data rewrite.
-- Identity is calculated transactionally from source identity and code-point target.
ALTER TABLE quiz_questions
    ADD COLUMN content_key VARCHAR(64) NULL,
    ADD CONSTRAINT uk_quiz_question_content UNIQUE (content_key);
