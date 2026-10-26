package com.japaneselearning.quiz.admin.service;

import com.japaneselearning.common.logging.StructuredLog;
import com.japaneselearning.common.logging.LogOperation;
import com.japaneselearning.common.logging.OperationLog;
import com.japaneselearning.common.exception.BusinessException;
import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.quiz.admin.dto.QuizImportResponse;
import com.japaneselearning.quiz.admin.dto.QuizImportResult;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class QuizImportService {
    private static final Logger LOG = Logger.getLogger(QuizImportService.class);

    private final QuizAdminWriteService writes;

    public QuizImportService(QuizAdminWriteService writes) {
        this.writes = writes;
    }

    // No outer session/transaction: each intercepted create owns and closes its transaction.
    @LogOperation("quiz.question.import")
    public Uni<QuizImportResponse> importQuestions(List<QuizImportItem> items) {
        return Multi.createFrom().range(0, items.size())
                .onSubscription().invoke(() -> StructuredLog.event(
                        LOG,
                        Logger.Level.INFO,
                        "quiz.question.import.batch_started",
                        Map.of("total", items.size())
                ))
                .onItem().transformToUniAndConcatenate(index -> createItem(index, items.get(index)))
                .collect().asList()
                .map(QuizImportResponse::from)
                .chain(result -> OperationLog.outcome(
                        Uni.createFrom().item(result),
                        result.failed() > 0 ? "partial_failure" : "completed",
                        Map.of(
                                "total", result.total(),
                                "succeeded", result.imported(),
                                "failed", result.failed()
                        )
                ));
    }

    private Uni<QuizImportResult> createItem(int index, QuizImportItem item) {
        if (!item.errors().isEmpty()) {
            return Uni.createFrom().item(QuizImportResult.failed(index, item.errors()));
        }

        return Uni.createFrom().deferred(() -> writes.create(item.request()))
                .map(question -> QuizImportResult.imported(index, question))
                // Recovery is outside the intercepted create, after rollback and session cleanup.
                .onFailure(failure -> failure instanceof ValidationException
                        || failure instanceof ResourceNotFoundException
                        || failure instanceof ConflictException)
                .recoverWithItem(failure -> QuizImportResult.failed(
                        index, QuizImportErrors.indexed(index, (BusinessException) failure)
                ));
    }
}
