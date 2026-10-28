package com.japaneselearning.common.logging;

import com.japaneselearning.flashcard.service.SpacedRepetitionService;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.json.runtime.JsonFormatter;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.MDC;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class StructuredLogTest {
    @Test
    void businessFieldsKeepNumericTypesAndUnsupportedBooleansStayExcluded() throws Exception {
        var formatter = new JsonFormatter();
        formatter.setDiscoveredProviders(List.of(new ApplicationJsonFields()));
        formatter.setExcludedKeys(Set.of("mdc", "ndc"));
        var json = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        for (String state : List.of("true", "false", "TRUE", "credential-sentinel")) {
            var record = new ExtLogRecord(org.jboss.logmanager.Level.INFO, "Business summary", getClass().getName());
            record.setMdc(Map.of("bookmarked", state, "lapses", "2", "studiedBasisPoints", "3333",
                    "masteryBasisPoints", "10000", "dueCount", "4", "studiedCount", "3",
                    "masteredCount", "2", "learning.previous_status", "NEW", "subject", "private-sentinel"));
            String output = formatter.format(record);
            var value = json.readTree(output);
            for (String key : List.of("lapses", "studiedBasisPoints", "masteryBasisPoints",
                    "dueCount", "studiedCount", "masteredCount")) {
                assertTrue(value.path(key).isIntegralNumber(), key);
            }
            assertEquals(3333, value.path("studiedBasisPoints").asInt());
            assertFalse(value.has("bookmarked"));
            assertFalse(output.contains("sentinel"));
        }
    }

    @Test
    void descriptiveOverloadPreservesEventNumericJsonAndMdcWhileLegacyMessageStaysUnchanged() throws Exception {
        Logger logger = Logger.getLogger("test.structured.message");
        var records = new java.util.ArrayList<ExtLogRecord>();
        Handler handler = new Handler() {
            public void publish(LogRecord record) {
                var extended = (ExtLogRecord) record;
                extended.copyMdc();
                records.add(extended);
            }

            public void flush() {
            }

            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            MDC.put("event.name", "outer.event");
            MDC.put("correlationId", "request-42");
            var applicationLogger = org.jboss.logging.Logger.getLogger(logger.getName());
            StructuredLog.event(applicationLogger, org.jboss.logging.Logger.Level.INFO, "quiz.question.list.completed",
                    "Successfully retrieved 2 quiz questions", Map.of("count", 2, "total", 9L));
            StructuredLog.event(applicationLogger, org.jboss.logging.Logger.Level.INFO, "legacy.completed", Map.of());
            assertEquals("outer.event", MDC.get("event.name"));
            assertNull(MDC.get("count"));
            assertEquals("legacy.completed", records.get(1).getFormattedMessage());
            var formatter = new JsonFormatter();
            formatter.setDiscoveredProviders(List.of(new ApplicationJsonFields()));
            formatter.setExcludedKeys(Set.of("mdc", "ndc"));
            var value = new ObjectMapper().readTree(formatter.format(records.get(0)));
            assertEquals("Successfully retrieved 2 quiz questions", value.path("message").asText());
            assertEquals("quiz.question.list.completed", value.path("event.name").asText());
            assertTrue(value.path("count").isIntegralNumber());
            assertEquals(2, value.path("count").asInt());
            assertEquals(9, value.path("total").asInt());
            assertEquals("request-42", value.path("correlationId").asText());
        } finally {
            logger.removeHandler(handler);
            MDC.clear();
        }
    }

    @Test
    void malformedNumbersCannotDropRecordsAndSensitiveMdcIsExcluded() throws Exception {
        var formatter = new JsonFormatter();
        formatter.setDiscoveredProviders(List.of(new ApplicationJsonFields()));
        formatter.setExcludedKeys(Set.of("mdc", "ndc"));
        var json = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        for (String invalid : List.of("", "NaN", "1.5", "9223372036854775808", "credential-sentinel")) {
            var record = new ExtLogRecord(org.jboss.logmanager.Level.INFO, "test.event", getClass().getName());
            record.setMdc(Map.of("durationMs", invalid, "count", "42", "reason", "123",
                    "event.name", "test.event", "authorization", "credential-sentinel"));
            String output = formatter.format(record);
            var value = json.readTree(output);
            assertFalse(value.has("durationMs"));
            assertTrue(value.path("count").isIntegralNumber());
            assertEquals(42, value.path("count").asInt());
            assertTrue(value.path("reason").isTextual());
            assertEquals("test.event", value.path("event.name").asText());
            assertFalse(output.contains("credential-sentinel"));
        }
    }

    @Test
    void srsDecisionIsDebugOnlyAndContainsSafeSchedulingFields() {
        Logger logger = Logger.getLogger(SpacedRepetitionService.class.getName());
        var previousLevel = logger.getLevel();
        var records = new java.util.ArrayList<ExtLogRecord>();
        Handler handler = new Handler() {
            public void publish(LogRecord record) {
                var extended = (ExtLogRecord) record;
                extended.copyMdc();
                records.add(extended);
            }

            public void flush() {
            }

            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            var service = new com.japaneselearning.flashcard.service.SpacedRepetitionService(600, 365, 5, 21);
            var state = com.japaneselearning.flashcard.domain.ProgressState.unseen();
            var rating = com.japaneselearning.flashcard.domain.ReviewRating.GOOD;
            var now = java.time.Instant.parse("2026-10-26T00:00:00Z");
            logger.setLevel(java.util.logging.Level.INFO);
            service.review(state, rating, now);
            assertTrue(records.isEmpty());
            logger.setLevel(java.util.logging.Level.FINE);
            service.review(state, rating, now);
            assertEquals(1, records.size());
            assertEquals("flashcard.srs.scheduled", records.get(0).getMdc("event.name"));
            assertEquals("GOOD", records.get(0).getMdc("reason"));
            assertEquals("86400", records.get(0).getMdc("intervalSeconds"));
            assertEquals("LEARNING", records.get(0).getMdc("learning.status"));
            assertEquals("NEW", records.get(0).getMdc("learning.previous_status"));
            assertEquals("0", records.get(0).getMdc("lapses"));
            assertNull(MDC.get("intervalSeconds"));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
        }
    }

    @Test
    void scopedFieldsRestorePreviousValuesAndDisabledEventsDoNotStringifyValues() {
        Logger logger = Logger.getLogger("test.structured.scope");
        var previousLevel = logger.getLevel();
        Handler handler = new Handler() {
            public void publish(LogRecord record) {
                assertEquals("new", MDC.get("operation"));
                assertEquals("test.event", MDC.get("event.name"));
            }

            public void flush() {
            }

            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            MDC.put("operation", "outer");
            MDC.put("correlationId", "request-42");
            logger.setLevel(java.util.logging.Level.INFO);
            var applicationLogger = org.jboss.logging.Logger.getLogger(logger.getName());
            StructuredLog.event(applicationLogger, org.jboss.logging.Logger.Level.INFO,
                    "test.event", Map.of("operation", "new", "count", 1));
            assertEquals("outer", MDC.get("operation"));
            assertEquals("request-42", MDC.get("correlationId"));
            assertNull(MDC.get("count"));
            assertNull(MDC.get("event.name"));
            Object unsafe = new Object() {
                public String toString() {
                    throw new AssertionError("Disabled event evaluated");
                }
            };
            StructuredLog.event(applicationLogger, org.jboss.logging.Logger.Level.DEBUG,
                    "test.disabled", Map.of("reason", unsafe));
        } finally {
            MDC.clear();
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
        }
    }
}
