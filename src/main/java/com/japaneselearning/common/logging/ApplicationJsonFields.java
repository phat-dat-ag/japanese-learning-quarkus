package com.japaneselearning.common.logging;

import io.quarkus.logging.json.runtime.JsonFormatter.JsonLogGenerator;
import io.quarkus.logging.json.runtime.JsonProvider;
import org.jboss.logmanager.ExtLogRecord;

import java.util.Set;

// The official formatter owns escaping and NDJSON framing. Only allowlisted MDC is exported.
public class ApplicationJsonFields implements JsonProvider {
    private static final Set<String> TEXT = Set.of(
            "event.name", "traceId", "spanId", "correlationId", "legacyTraceId", "http.request.method", "http.route",
            "learning.status", "learning.previous_status", "outcome", "error.type", "error.code",
            "error.stack_trace", "operation", "reason"
    );
    private static final Set<String> NUMBERS = Set.of(
            "page", "size", "intervalSeconds", "repetitions", "http.response.status_code", "durationMs", "sessionId",
            "itemId", "vocabularyId", "lessonId", "levelId", "questionId", "readingId", "meaningId", "exampleId",
            "pitchAccentId", "kanjiId", "total", "count", "created", "updated", "succeeded", "failed",
            "lapses", "dueCount", "studiedCount", "masteredCount", "studiedBasisPoints", "masteryBasisPoints"
    );

    @Override
    public void writeTo(JsonLogGenerator generator, ExtLogRecord record) throws Exception {
        for (String key : TEXT) {
            String value = record.getMdc(key);

            if (value != null) {
                generator.add(key, value);
            }
        }

        for (String key : NUMBERS) {
            String value = record.getMdc(key);

            if (value != null) {
                try {
                    generator.add(key, Long.parseLong(value));
                } catch (NumberFormatException ignored) {
                    // Omit malformed fields, never lose the record or change its numeric schema.
                }
            }
        }
    }
}
