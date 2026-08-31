-- ============================================================
-- LESSONS
-- Migration: V5
-- ============================================================


-- ============================================================
-- 1. LESSONS
-- ============================================================

CREATE TABLE lessons (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    level_id BIGINT UNSIGNED NOT NULL,

    lesson_number INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NULL,

    display_order INT NOT NULL,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT fk_lessons_level
        FOREIGN KEY (level_id)
        REFERENCES jlpt_levels(id)
        ON DELETE RESTRICT,

    CONSTRAINT uk_lessons_level_number
        UNIQUE (level_id, lesson_number),

    CONSTRAINT uk_lessons_level_order
        UNIQUE (level_id, display_order),

    INDEX idx_lessons_level
        (level_id, display_order)
) ENGINE=InnoDB;


-- ============================================================
-- 2. LESSON <-> VOCABULARY
-- ============================================================

CREATE TABLE lesson_vocabulary (
    lesson_id BIGINT UNSIGNED NOT NULL,
    vocabulary_id BIGINT UNSIGNED NOT NULL,

    display_order INT NOT NULL DEFAULT 0,

    PRIMARY KEY (lesson_id, vocabulary_id),

    CONSTRAINT fk_lesson_vocabulary_lesson
        FOREIGN KEY (lesson_id)
        REFERENCES lessons(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_lesson_vocabulary_vocabulary
        FOREIGN KEY (vocabulary_id)
        REFERENCES vocabulary(id)
        ON DELETE RESTRICT,

    INDEX idx_lesson_vocabulary_lesson
        (lesson_id, display_order),

    INDEX idx_lesson_vocabulary_vocabulary
        (vocabulary_id)
) ENGINE=InnoDB;