package com.japaneselearning.vocabulary.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "lesson_contents")
public class LessonContent {
    @Id
    @Column(name = "lesson_id", nullable = false)
    public Long lessonId;

    @Column(name = "schema_version", nullable = false)
    public int schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    public JsonNode content;

    // The service owns revisions under the parent row lock; do not add @Version.
    @Column(nullable = false)
    public long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;
}
