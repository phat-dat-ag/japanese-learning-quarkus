-- Content revisions are independent of the structured document schema version.
-- UTC timestamps are supplied by the application.
CREATE TABLE lesson_contents (
    lesson_id BIGINT UNSIGNED NOT NULL,
    schema_version INT NOT NULL,
    content JSON NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (lesson_id),
    CONSTRAINT fk_lesson_contents_lesson FOREIGN KEY (lesson_id)
        REFERENCES lessons(id) ON DELETE CASCADE,
    CONSTRAINT ck_lesson_contents_schema CHECK (schema_version >= 1),
    CONSTRAINT ck_lesson_contents_version CHECK (version >= 0),
    CONSTRAINT ck_lesson_contents_object CHECK (JSON_TYPE(content) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
