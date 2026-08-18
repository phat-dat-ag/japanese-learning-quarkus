-- ============================================================
-- JLPT VOCABULARY FLASHCARD - INITIAL SCHEMA
-- Database: MySQL 8.4
-- Migration: V1
-- ============================================================


-- ============================================================
-- 1. JLPT LEVELS
-- ============================================================

CREATE TABLE jlpt_levels (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(10) NOT NULL,
    name VARCHAR(50) NOT NULL,
    display_order INT NOT NULL,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    CONSTRAINT uk_jlpt_levels_code UNIQUE (code),
    CONSTRAINT uk_jlpt_levels_display_order UNIQUE (display_order)
) ENGINE=InnoDB;


-- ============================================================
-- 2. VOCABULARY
-- ============================================================

CREATE TABLE vocabulary (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    word VARCHAR(100) NOT NULL,
    normalized_word VARCHAR(100) NOT NULL,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    INDEX idx_vocabulary_word (word),
    INDEX idx_vocabulary_normalized_word (normalized_word)
) ENGINE=InnoDB;


-- ============================================================
-- 3. VOCABULARY <-> JLPT LEVEL
-- ============================================================

CREATE TABLE vocabulary_levels (
    vocabulary_id BIGINT UNSIGNED NOT NULL,
    level_id BIGINT UNSIGNED NOT NULL,

    display_order INT NULL,

    PRIMARY KEY (vocabulary_id, level_id),

    CONSTRAINT fk_vocabulary_levels_vocabulary
        FOREIGN KEY (vocabulary_id)
        REFERENCES vocabulary(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_vocabulary_levels_level
        FOREIGN KEY (level_id)
        REFERENCES jlpt_levels(id)
        ON DELETE RESTRICT,

    INDEX idx_vocabulary_levels_level
        (level_id, display_order)
) ENGINE=InnoDB;


-- ============================================================
-- 4. VOCABULARY READINGS
-- ============================================================

CREATE TABLE vocabulary_readings (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    vocabulary_id BIGINT UNSIGNED NOT NULL,

    reading VARCHAR(100) NOT NULL,

    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 0,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT fk_vocabulary_readings_vocabulary
        FOREIGN KEY (vocabulary_id)
        REFERENCES vocabulary(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_vocabulary_readings
        UNIQUE (vocabulary_id, reading),

    INDEX idx_vocabulary_readings_vocabulary
        (vocabulary_id, display_order)
) ENGINE=InnoDB;


-- ============================================================
-- 5. VOCABULARY MEANINGS
-- ============================================================

CREATE TABLE vocabulary_meanings (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    vocabulary_id BIGINT UNSIGNED NOT NULL,

    language_code VARCHAR(5) NOT NULL,
    meaning VARCHAR(500) NOT NULL,

    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 0,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT fk_vocabulary_meanings_vocabulary
        FOREIGN KEY (vocabulary_id)
        REFERENCES vocabulary(id)
        ON DELETE CASCADE,

    INDEX idx_vocabulary_meanings_vocabulary
        (vocabulary_id, language_code, display_order)
) ENGINE=InnoDB;


-- ============================================================
-- 6. PARTS OF SPEECH
-- ============================================================

CREATE TABLE parts_of_speech (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    code VARCHAR(50) NOT NULL,
    name_vi VARCHAR(100) NOT NULL,
    name_en VARCHAR(100) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_parts_of_speech_code UNIQUE (code)
) ENGINE=InnoDB;


-- ============================================================
-- 7. VOCABULARY <-> PARTS OF SPEECH
-- ============================================================

CREATE TABLE vocabulary_parts_of_speech (
    vocabulary_id BIGINT UNSIGNED NOT NULL,
    part_of_speech_id BIGINT UNSIGNED NOT NULL,

    PRIMARY KEY (vocabulary_id, part_of_speech_id),

    CONSTRAINT fk_vocabulary_pos_vocabulary
        FOREIGN KEY (vocabulary_id)
        REFERENCES vocabulary(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_vocabulary_pos_part_of_speech
        FOREIGN KEY (part_of_speech_id)
        REFERENCES parts_of_speech(id)
        ON DELETE RESTRICT
) ENGINE=InnoDB;


-- ============================================================
-- 8. KANJI
-- ============================================================

CREATE TABLE kanji (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    kanji_character VARCHAR(10) NOT NULL,

    stroke_count SMALLINT UNSIGNED NULL,
    meaning_vi VARCHAR(500) NULL,
    meaning_en VARCHAR(500) NULL,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT uk_kanji_character UNIQUE (kanji_character)
) ENGINE=InnoDB;


-- ============================================================
-- 9. VOCABULARY <-> KANJI
-- ============================================================

CREATE TABLE vocabulary_kanji (
    vocabulary_id BIGINT UNSIGNED NOT NULL,
    kanji_id BIGINT UNSIGNED NOT NULL,

    display_order INT NOT NULL DEFAULT 0,

    PRIMARY KEY (vocabulary_id, kanji_id),

    CONSTRAINT fk_vocabulary_kanji_vocabulary
        FOREIGN KEY (vocabulary_id)
        REFERENCES vocabulary(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_vocabulary_kanji_kanji
        FOREIGN KEY (kanji_id)
        REFERENCES kanji(id)
        ON DELETE RESTRICT,

    INDEX idx_vocabulary_kanji_vocabulary
        (vocabulary_id, display_order),

    INDEX idx_vocabulary_kanji_kanji
        (kanji_id)
) ENGINE=InnoDB;


-- ============================================================
-- 10. KANJI READINGS
-- ============================================================

CREATE TABLE kanji_readings (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    kanji_id BIGINT UNSIGNED NOT NULL,

    reading VARCHAR(100) NOT NULL,
    reading_type VARCHAR(20) NOT NULL,

    display_order INT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),

    CONSTRAINT fk_kanji_readings_kanji
        FOREIGN KEY (kanji_id)
        REFERENCES kanji(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_kanji_readings
        UNIQUE (kanji_id, reading, reading_type),

    INDEX idx_kanji_readings_kanji
        (kanji_id, display_order)
) ENGINE=InnoDB;


-- ============================================================
-- 11. VOCABULARY PITCH ACCENTS
-- ============================================================

CREATE TABLE vocabulary_pitch_accents (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    vocabulary_reading_id BIGINT UNSIGNED NOT NULL,

    accent_pattern SMALLINT UNSIGNED NOT NULL,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT fk_pitch_accents_reading
        FOREIGN KEY (vocabulary_reading_id)
        REFERENCES vocabulary_readings(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_pitch_accents_reading_pattern
        UNIQUE (vocabulary_reading_id, accent_pattern),

    INDEX idx_pitch_accents_reading
        (vocabulary_reading_id)
) ENGINE=InnoDB;


-- ============================================================
-- 12. EXAMPLE SENTENCES
-- ============================================================

CREATE TABLE example_sentences (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    japanese_text VARCHAR(1000) NOT NULL,
    japanese_reading VARCHAR(1000) NOT NULL,

    meaning_vi VARCHAR(1000) NOT NULL,
    meaning_en VARCHAR(1000) NOT NULL,

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id)
) ENGINE=InnoDB;


-- ============================================================
-- 13. VOCABULARY <-> EXAMPLE SENTENCES
-- ============================================================

CREATE TABLE vocabulary_examples (
    vocabulary_id BIGINT UNSIGNED NOT NULL,
    example_sentence_id BIGINT UNSIGNED NOT NULL,

    target_text VARCHAR(200) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,

    PRIMARY KEY (vocabulary_id, example_sentence_id),

    CONSTRAINT fk_vocabulary_examples_vocabulary
        FOREIGN KEY (vocabulary_id)
        REFERENCES vocabulary(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_vocabulary_examples_sentence
        FOREIGN KEY (example_sentence_id)
        REFERENCES example_sentences(id)
        ON DELETE CASCADE,

    INDEX idx_vocabulary_examples_vocabulary
        (vocabulary_id, display_order),

    INDEX idx_vocabulary_examples_sentence
        (example_sentence_id)
) ENGINE=InnoDB;