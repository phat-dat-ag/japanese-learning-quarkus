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
