package com.japaneselearning.common.logging;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.json.runtime.JsonFormatter;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalLoggingTest {
    @Test
    void typedCanonicalFieldsOmitPrivateDataAndInvalidSpans() throws Exception {
        var formatter = new JsonFormatter();

        formatter.setDiscoveredProviders(List.of(new ApplicationJsonFields()));
        formatter.setExcludedKeys(Set.of("mdc", "ndc"));

        var record = new ExtLogRecord(Level.WARN, "Request rejected", getClass().getName());

        record.setMdc(
                Map.ofEntries(
                        Map.entry("event.name", "http.request.failed"),
                        Map.entry("http.request.method", "GET"),
                        Map.entry("http.route", "/api/v1/lessons/{lessonId}/content"),
                        Map.entry("http.response.status_code", "403"),
                        Map.entry("durationMs", "12"),
                        Map.entry("correlationId", "test-request"),
                        Map.entry("traceId", "0".repeat(32)),
                        Map.entry("spanId", "0".repeat(16)),
                        Map.entry("legacyTraceId", "legacy-only"),
                        Map.entry("sessionId", "123456789"),
                        Map.entry("network.peer.address", "private-sentinel"),
                        Map.entry("Authorization", "Bearer token-sentinel"),
                        Map.entry("password", "password-sentinel"),
                        Map.entry("Cookie", "cookie-sentinel"),
                        Map.entry("refreshToken", "refresh-sentinel"),
                        Map.entry("connectionString", "Password=database-sentinel"),
                        Map.entry("privateKey", "-----BEGIN PRIVATE KEY-----key-sentinel")
                )
        );

        String output = formatter.format(record);

        var json =
                new ObjectMapper()
                        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .readTree(output);

        assertEquals("vocabulary-api", json.path("service_name").asText());
        assertEquals("http.request.completed", json.path("event_name").asText());
        assertEquals("grammar", json.path("route_family").asText());
        assertEquals("authorization", json.path("error_category").asText());
        assertEquals("test-request", json.path("correlation_id").asText());
        assertEquals("legacy-only", json.path("legacy_trace_id").asText());
        assertTrue(json.path("duration_ms").isIntegralNumber());
        assertTrue(json.path("http_status").isIntegralNumber());
        assertFalse(json.has("trace_id"));
        assertFalse(json.has("span_id"));
        assertFalse(json.has("traceId"));
        assertFalse(json.has("sessionId"));
        assertFalse(output.contains("sentinel"));
        assertFalse(output.contains("123456789"));
    }

    @Test
    void routeFamiliesDistinguishQuizVocabularyFlashcardAndGrammar() {
        assertEquals("quiz", ApplicationJsonFields.routeFamily("/api/v1/kanji-quiz/sessions"));
        assertEquals(
                "quiz_admin",
                ApplicationJsonFields.routeFamily("/api/v1/admin/kanji-quiz/questions/{id}")
        );
        assertEquals(
                "vocabulary_admin",
                ApplicationJsonFields.routeFamily("/api/v1/admin/vocabularies/{vocabularyId}")
        );
        assertEquals("flashcard", ApplicationJsonFields.routeFamily("/api/v1/flashcards"));
        assertEquals(
                "grammar",
                ApplicationJsonFields.routeFamily("/api/v1/lessons/{lessonId}/content")
        );
        assertEquals("other", ApplicationJsonFields.routeFamily("/unmatched"));
    }
}
