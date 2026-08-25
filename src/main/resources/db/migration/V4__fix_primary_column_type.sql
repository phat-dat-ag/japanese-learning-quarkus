ALTER TABLE vocabulary_meanings
    MODIFY COLUMN is_primary BIT NOT NULL;

ALTER TABLE vocabulary_readings
    MODIFY COLUMN is_primary BIT NOT NULL;