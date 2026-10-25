-- UTC DATETIME values are written explicitly by the application.
CREATE TABLE user_vocabulary_progress (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_subject VARCHAR(255) NOT NULL,
    vocabulary_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL,
    successful_repetition_count BIGINT NOT NULL,
    lapse_count BIGINT NOT NULL,
    interval_seconds BIGINT NOT NULL,
    ease_factor DOUBLE NOT NULL,
    last_reviewed_at DATETIME(6) NOT NULL,
    next_review_at DATETIME(6) NOT NULL,
    mastered_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT uk_flashcard_progress_owner UNIQUE (user_subject, vocabulary_id),
    CONSTRAINT fk_flashcard_progress_vocabulary FOREIGN KEY (vocabulary_id) REFERENCES vocabulary(id) ON DELETE RESTRICT,
    CONSTRAINT ck_flashcard_progress_subject CHECK (CHAR_LENGTH(TRIM(user_subject)) > 0),
    CONSTRAINT ck_flashcard_progress_state CHECK (status IN ('LEARNING', 'REVIEW', 'MASTERED')),
    CONSTRAINT ck_flashcard_progress_counts CHECK (successful_repetition_count >= 0 AND lapse_count >= 0 AND interval_seconds > 0 AND version >= 0),
    CONSTRAINT ck_flashcard_progress_ease CHECK (ease_factor BETWEEN 1.3 AND 3.0),
    CONSTRAINT ck_flashcard_progress_mastery CHECK ((status = 'MASTERED' AND mastered_at IS NOT NULL) OR (status <> 'MASTERED' AND mastered_at IS NULL)),
    CONSTRAINT ck_flashcard_progress_schedule CHECK (next_review_at > last_reviewed_at),

    INDEX idx_flashcard_progress_due (user_subject, next_review_at, vocabulary_id),
    INDEX idx_flashcard_progress_status (user_subject, status, vocabulary_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE user_vocabulary_bookmarks (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_subject VARCHAR(255) NOT NULL,
    vocabulary_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL,

    CONSTRAINT uk_flashcard_bookmark_owner UNIQUE (user_subject, vocabulary_id),
    CONSTRAINT fk_flashcard_bookmark_vocabulary FOREIGN KEY (vocabulary_id) REFERENCES vocabulary(id) ON DELETE RESTRICT,
    CONSTRAINT ck_flashcard_bookmark_subject CHECK (CHAR_LENGTH(TRIM(user_subject)) > 0),

    INDEX idx_flashcard_bookmark_created (user_subject, created_at, vocabulary_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE flashcard_sessions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_subject VARCHAR(255) NOT NULL,
    lesson_id BIGINT UNSIGNED NOT NULL,
    mode VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    item_count INT NOT NULL,
    completed_count INT NOT NULL DEFAULT 0,
    completed_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT fk_flashcard_session_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE RESTRICT,
    CONSTRAINT ck_flashcard_session_subject CHECK (CHAR_LENGTH(TRIM(user_subject)) > 0),
    CONSTRAINT ck_flashcard_session_mode CHECK (mode IN ('NEW_AND_DUE', 'DUE', 'ALL')),
    CONSTRAINT ck_flashcard_session_counts CHECK (item_count BETWEEN 1 AND 100 AND completed_count BETWEEN 0 AND item_count AND version >= 0),
    CONSTRAINT ck_flashcard_session_status CHECK (
        (status = 'IN_PROGRESS' AND completed_count < item_count AND completed_at IS NULL) OR
        (status = 'COMPLETED' AND completed_count = item_count AND completed_at IS NOT NULL)
    ),

    INDEX idx_flashcard_session_owner (user_subject, status, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE flashcard_session_items (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT UNSIGNED NOT NULL,
    vocabulary_id BIGINT UNSIGNED NOT NULL,
    position INT NOT NULL,
    completed_at DATETIME(6) NULL,

    CONSTRAINT fk_flashcard_item_session FOREIGN KEY (session_id) REFERENCES flashcard_sessions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_flashcard_item_vocabulary FOREIGN KEY (vocabulary_id) REFERENCES vocabulary(id) ON DELETE RESTRICT,
    CONSTRAINT uk_flashcard_item_position UNIQUE (session_id, position),
    CONSTRAINT uk_flashcard_item_vocabulary UNIQUE (session_id, vocabulary_id),
    CONSTRAINT ck_flashcard_item_position CHECK (position BETWEEN 0 AND 99)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE flashcard_review_history (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_item_id BIGINT UNSIGNED NOT NULL,
    user_subject VARCHAR(255) NOT NULL,
    vocabulary_id BIGINT UNSIGNED NOT NULL,
    rating VARCHAR(8) NOT NULL,
    scheduling BOOLEAN NOT NULL,
    expected_progress_version BIGINT NOT NULL,
    algorithm_version VARCHAR(40) NOT NULL,
    before_state LONGTEXT NOT NULL,
    after_state LONGTEXT NOT NULL,
    reviewed_at DATETIME(6) NOT NULL,

    CONSTRAINT uk_flashcard_review_item UNIQUE (session_item_id),
    CONSTRAINT fk_flashcard_review_item FOREIGN KEY (session_item_id) REFERENCES flashcard_session_items(id) ON DELETE RESTRICT,
    CONSTRAINT fk_flashcard_review_vocabulary FOREIGN KEY (vocabulary_id) REFERENCES vocabulary(id) ON DELETE RESTRICT,
    CONSTRAINT ck_flashcard_review_subject CHECK (CHAR_LENGTH(TRIM(user_subject)) > 0),
    CONSTRAINT ck_flashcard_review_rating CHECK (rating IN ('AGAIN', 'HARD', 'GOOD', 'EASY')),
    CONSTRAINT ck_flashcard_review_version CHECK (expected_progress_version >= 0),
    CONSTRAINT ck_flashcard_review_json CHECK (JSON_VALID(before_state) AND JSON_VALID(after_state)),

    INDEX idx_flashcard_review_owner (user_subject, vocabulary_id, reviewed_at, id),
    INDEX idx_flashcard_review_forgotten (user_subject, rating, vocabulary_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
