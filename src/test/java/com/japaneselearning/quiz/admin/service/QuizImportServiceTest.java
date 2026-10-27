package com.japaneselearning.quiz.admin.service;

import com.japaneselearning.quiz.admin.dto.QuizQuestionCreateRequest;
import com.japaneselearning.quiz.admin.dto.QuizQuestionResponse;
import io.smallrye.mutiny.Uni;
import org.hibernate.exception.JDBCConnectionException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuizImportServiceTest {
    @Test
    void interruptedBatchCountsCommittedItemsPerSubscriptionAndDoesNotLogExceptionText() {
        var logger = java.util.logging.Logger.getLogger(QuizImportService.class.getName());
        var records = new java.util.ArrayList<org.jboss.logmanager.ExtLogRecord>();
        var handler = new java.util.logging.Handler() {
            public void publish(java.util.logging.LogRecord record) {
                var extended = (org.jboss.logmanager.ExtLogRecord) record;
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
            AtomicInteger calls = new AtomicInteger();
            var failure = new IllegalStateException("secret-database-value");
            var writes = new QuizAdminWriteService(null, null, null, null, null) {
                public Uni<QuizQuestionResponse> create(QuizQuestionCreateRequest request) {
                    if (calls.incrementAndGet() % 2 == 0) {
                        return Uni.createFrom().failure(failure);
                    }
                    return Uni.createFrom().item(new QuizQuestionResponse(15L, 0L,
                            com.japaneselearning.quiz.domain.QuestionSource.CUSTOM,
                            com.japaneselearning.quiz.domain.QuestionStatus.DRAFT,
                            null, null, "secret-content", 0, 1, "secret-target", null, null,
                            false, false, List.of(), List.of(), List.of(), null, null));
                }
            };
            var service = new QuizImportService(writes);
            var item = new QuizImportItem(new QuizQuestionCreateRequest(null, null), List.of());
            var result = service.importQuestions(List.of(item, item, item));
            org.junit.jupiter.api.Assertions.assertTrue(records.isEmpty());
            for (int subscription = 0; subscription < 2; subscription++) {
                records.clear();
                assertSame(failure, assertThrows(IllegalStateException.class,
                        () -> result.await().atMost(Duration.ofSeconds(5))));
                var summaries = records.stream().filter(record ->
                        "quiz.question.import.interrupted".equals(record.getMdc("event.name"))).toList();
                assertEquals(1, summaries.size());
                var summary = summaries.get(0);
                assertEquals("1", summary.getMdc("count"));
                assertEquals("3", summary.getMdc("total"));
                assertEquals("1", summary.getMdc("created"));
                assertEquals("1", summary.getMdc("failed"));
                assertEquals(org.jboss.logmanager.Level.ERROR, summary.getLevel());
                org.junit.jupiter.api.Assertions.assertNull(summary.getThrown());
                org.junit.jupiter.api.Assertions.assertFalse(summary.getFormattedMessage().contains("secret"));
                org.junit.jupiter.api.Assertions.assertTrue(summary.getFormattedMessage().contains("created=1"));
            }
            assertEquals(4, calls.get(), "The third item must never be attempted");
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    void infrastructureFailuresPropagateAndStopFurtherItems() {
        for (RuntimeException failure : List.of(
                new IllegalStateException("Unexpected infrastructure failure"),
                new JDBCConnectionException("Database unavailable", new SQLException("Connection lost"))
        )) {
            AtomicInteger calls = new AtomicInteger();
            QuizAdminWriteService writes = new QuizAdminWriteService(null, null, null, null, null) {
                @Override
                public Uni<QuizQuestionResponse> create(QuizQuestionCreateRequest request) {
                    calls.incrementAndGet();
                    return Uni.createFrom().failure(failure);
                }
            };
            QuizImportService service = new QuizImportService(writes);
            QuizImportItem prepared = new QuizImportItem(new QuizQuestionCreateRequest(null, null), List.of());
            RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                    service.importQuestions(List.of(prepared, prepared)).await().atMost(Duration.ofSeconds(5)));
            assertSame(failure, thrown);
            assertEquals(1, calls.get());
        }
    }
}
