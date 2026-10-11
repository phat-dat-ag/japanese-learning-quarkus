-- Quiz owns CUSTOM readings only. EXAMPLE readings remain in example_sentences.
CREATE TABLE quiz_questions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_type VARCHAR(16) NOT NULL,
    example_sentence_id BIGINT UNSIGNED NULL,
    vocabulary_id BIGINT UNSIGNED NULL,
    sentence_reading VARCHAR(1000) NULL,
    target_start INT NOT NULL,
    target_length INT NOT NULL,
    target_reading VARCHAR(200) NOT NULL,
    explanation_vi VARCHAR(2000) NULL,
    explanation_en VARCHAR(2000) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    validated_example_fingerprint VARCHAR(64) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_quiz_question_example FOREIGN KEY (example_sentence_id) REFERENCES example_sentences(id) ON DELETE SET NULL,
    CONSTRAINT fk_quiz_question_vocabulary FOREIGN KEY (vocabulary_id) REFERENCES vocabulary(id) ON DELETE SET NULL,
    CONSTRAINT ck_quiz_question_source CHECK (
        (source_type = 'EXAMPLE' AND sentence_reading IS NULL) OR
        (source_type = 'CUSTOM' AND sentence_reading IS NOT NULL
            AND CHAR_LENGTH(TRIM(sentence_reading)) > 0 AND validated_example_fingerprint IS NULL)),
    CONSTRAINT ck_quiz_question_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_quiz_question_target CHECK (target_start >= 0 AND target_length > 0
        AND CHAR_LENGTH(target_reading) = target_length),
    CONSTRAINT ck_quiz_question_version CHECK (version >= 0),
    INDEX idx_quiz_question_status (status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE quiz_question_options (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT UNSIGNED NOT NULL,
    option_text VARCHAR(200) NOT NULL,
    is_correct BIT(1) NOT NULL,
    correct_marker TINYINT GENERATED ALWAYS AS (IF(is_correct, 1, NULL)) STORED,
    CONSTRAINT fk_quiz_option_question FOREIGN KEY (question_id) REFERENCES quiz_questions(id) ON DELETE CASCADE,
    CONSTRAINT uk_quiz_option_text UNIQUE (question_id, option_text),
    CONSTRAINT uk_quiz_option_correct UNIQUE (question_id, correct_marker),
    CONSTRAINT ck_quiz_option_text CHECK (CHAR_LENGTH(TRIM(option_text)) > 0),
    CONSTRAINT ck_quiz_option_correct CHECK (is_correct IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

-- Explicit classification supports standalone CUSTOM questions and avoids ambiguous
-- inheritance from examples shared by several vocabularies. A lesson also implies its level.
CREATE TABLE quiz_question_levels (
    question_id BIGINT UNSIGNED NOT NULL,
    level_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (question_id, level_id),
    CONSTRAINT fk_quiz_level_question FOREIGN KEY (question_id) REFERENCES quiz_questions(id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_level_level FOREIGN KEY (level_id) REFERENCES jlpt_levels(id) ON DELETE CASCADE,
    INDEX idx_quiz_level_questions (level_id, question_id)
) ENGINE=InnoDB;

CREATE TABLE quiz_question_lessons (
    question_id BIGINT UNSIGNED NOT NULL,
    lesson_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (question_id, lesson_id),
    CONSTRAINT fk_quiz_lesson_question FOREIGN KEY (question_id) REFERENCES quiz_questions(id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_lesson_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE,
    INDEX idx_quiz_lesson_questions (lesson_id, question_id)
) ENGINE=InnoDB;

CREATE TABLE quiz_sessions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_subject VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS',
    question_count INT NOT NULL,
    completed_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_quiz_session_subject CHECK (CHAR_LENGTH(TRIM(user_subject)) > 0),
    CONSTRAINT ck_quiz_session_status CHECK (
        (status = 'IN_PROGRESS' AND completed_at IS NULL) OR
        (status IN ('COMPLETED', 'ABANDONED') AND completed_at IS NOT NULL)),
    CONSTRAINT ck_quiz_session_count CHECK (question_count > 0),
    INDEX idx_quiz_session_user (user_subject, status, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE quiz_session_questions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT UNSIGNED NOT NULL,
    question_id BIGINT UNSIGNED NULL,
    question_version BIGINT NOT NULL,
    question_number INT NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    source_example_id BIGINT UNSIGNED NULL,
    sentence_reading VARCHAR(1000) NOT NULL,
    target_start INT NOT NULL,
    target_length INT NOT NULL,
    target_reading VARCHAR(200) NOT NULL,
    explanation_vi VARCHAR(2000) NULL,
    explanation_en VARCHAR(2000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_quiz_snapshot_session FOREIGN KEY (session_id) REFERENCES quiz_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_snapshot_question FOREIGN KEY (question_id) REFERENCES quiz_questions(id) ON DELETE SET NULL,
    CONSTRAINT uk_quiz_snapshot_number UNIQUE (session_id, question_number),
    CONSTRAINT uk_quiz_snapshot_question UNIQUE (session_id, question_id),
    CONSTRAINT ck_quiz_snapshot_source CHECK (source_type IN ('EXAMPLE', 'CUSTOM')),
    CONSTRAINT ck_quiz_snapshot_number CHECK (question_number >= 0 AND question_version >= 0),
    CONSTRAINT ck_quiz_snapshot_target CHECK (target_start >= 0 AND target_length > 0
        AND target_start + target_length <= CHAR_LENGTH(sentence_reading)
        AND CAST(SUBSTRING(sentence_reading, target_start + 1, target_length) AS BINARY) = CAST(target_reading AS BINARY))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

-- No bank option FK: edits/deletes must never change an already assigned question.
CREATE TABLE quiz_session_options (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_question_id BIGINT UNSIGNED NOT NULL,
    option_text VARCHAR(200) NOT NULL,
    is_correct BIT(1) NOT NULL,
    correct_marker TINYINT GENERATED ALWAYS AS (IF(is_correct, 1, NULL)) STORED,
    CONSTRAINT fk_quiz_snapshot_option FOREIGN KEY (session_question_id) REFERENCES quiz_session_questions(id) ON DELETE CASCADE,
    CONSTRAINT uk_quiz_snapshot_option_text UNIQUE (session_question_id, option_text),
    CONSTRAINT uk_quiz_snapshot_correct UNIQUE (session_question_id, correct_marker),
    CONSTRAINT uk_quiz_snapshot_option_identity UNIQUE (session_question_id, id),
    CONSTRAINT ck_quiz_snapshot_option_text CHECK (CHAR_LENGTH(TRIM(option_text)) > 0),
    CONSTRAINT ck_quiz_snapshot_option_correct CHECK (is_correct IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

-- One immutable answer per assigned question. The composite FK rejects another
-- question's option. Correctness is read from the immutable option snapshot.
CREATE TABLE quiz_answers (
    session_question_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    selected_option_id BIGINT UNSIGNED NOT NULL,
    answered_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_quiz_answer_option FOREIGN KEY (session_question_id, selected_option_id)
        REFERENCES quiz_session_options(session_question_id, id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE quiz_user_progress (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_subject VARCHAR(255) NOT NULL,
    question_id BIGINT UNSIGNED NOT NULL,
    attempts BIGINT NOT NULL DEFAULT 0,
    correct_answers BIGINT NOT NULL DEFAULT 0,
    last_answered_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_quiz_progress_question FOREIGN KEY (question_id) REFERENCES quiz_questions(id) ON DELETE CASCADE,
    CONSTRAINT uk_quiz_progress_user_question UNIQUE (user_subject, question_id),
    CONSTRAINT ck_quiz_progress_subject CHECK (CHAR_LENGTH(TRIM(user_subject)) > 0),
    CONSTRAINT ck_quiz_progress_counts CHECK (attempts >= 0 AND correct_answers >= 0 AND correct_answers <= attempts
        AND ((attempts = 0 AND last_answered_at IS NULL) OR (attempts > 0 AND last_answered_at IS NOT NULL))),
    INDEX idx_quiz_progress_user_recent (user_subject, last_answered_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

-- Cross-row publication and Unicode invariants are enforced by QuizPublicationService
-- under a question lock. All future bank writers must use that transaction boundary.
