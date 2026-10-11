-- Historical classifications cannot be recovered from the mutable question bank.
ALTER TABLE quiz_session_questions
    ADD COLUMN classifications_captured BOOLEAN NOT NULL DEFAULT FALSE;

-- No master-data FKs: deletion/reclassification must not rewrite history.
CREATE TABLE quiz_session_question_levels (
    session_question_id BIGINT UNSIGNED NOT NULL,
    level_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (session_question_id, level_id),
    CONSTRAINT fk_quiz_snapshot_level_question FOREIGN KEY (session_question_id)
        REFERENCES quiz_session_questions(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE quiz_session_question_lessons (
    session_question_id BIGINT UNSIGNED NOT NULL,
    lesson_id BIGINT UNSIGNED NOT NULL,
    level_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (session_question_id, lesson_id),
    CONSTRAINT fk_quiz_snapshot_lesson_question FOREIGN KEY (session_question_id)
        REFERENCES quiz_session_questions(id) ON DELETE CASCADE
) ENGINE=InnoDB;
