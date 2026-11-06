package com.japaneselearning.vocabulary.dto.content;

import java.time.LocalDateTime;

public record LessonContentResponse(
        Long lessonId,
        int schemaVersion,
        long version,
        LessonContentDocument content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    // Creation identity prevents a stale editor targeting a deleted/recreated document at revision 1.
    public String etag() {
        return etag(lessonId, createdAt, version);
    }

    public static String etag(Long lessonId, LocalDateTime createdAt, long version) {
        return "\"" + lessonId + ":" + createdAt + ":" + version + "\"";
    }
}
