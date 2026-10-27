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

import java.util.ArrayList;
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
    @LogOperation(
            value = "quiz.question.import",
            description = "Quiz question import",
            successMessage = "Quiz import completed: processed={total}, created={created}, failed={failed}"
    )
    public Uni<QuizImportResponse> importQuestions(List<QuizImportItem> items) {
        return Uni.createFrom().deferred(() -> importBatch(items));
    }

    private Uni<QuizImportResponse> importBatch(List<QuizImportItem> items) {
        // Each subscription tracks only completed item transactions; an interrupted item is not counted as created.
        List<QuizImportResult> completed = new ArrayList<>();
        return Multi.createFrom().range(0, items.size())
                .onSubscription().invoke(() -> StructuredLog.event(
                        LOG,
                        Logger.Level.INFO,
                        "quiz.question.import.batch_started",
                        "Starting quiz import of " + items.size() + " question records",
                        Map.of("operation", "quiz.question.import", "total", items.size())
                ))
                .onSubscription().invoke(() -> StructuredLog.event(
                        LOG,
                        Logger.Level.DEBUG,
                        "quiz.question.import.validated",
                        "Validated quiz import structure; invalid records will be skipped",
                        Map.of(
                                "operation", "quiz.question.import",
                                "total", items.size(),
                                "failed", items.stream()
                                        .filter(item -> !item.errors().isEmpty())
                                        .count()
                        )
                ))
                .onItem().transformToUniAndConcatenate(index -> createItem(index, items.get(index)))
                .invoke(completed::add)
                .onFailure().invoke(failure -> {
                    QuizImportResponse summary = QuizImportResponse.from(completed);
                    StructuredLog.event(
                            LOG,
                            Logger.Level.ERROR,
                            "quiz.question.import.interrupted",
                            "Quiz import stopped after " + summary.total()
                                    + " completed records: created=" + summary.imported()
                                    + ", rejected=" + summary.failed()
                                    + "; the current record failed and later records were not processed",
                            Map.of(
                                    "operation", "quiz.question.import",
                                    "outcome", "failure",
                                    "count", summary.total(),
                                    "total", items.size(),
                                    "created", summary.imported(),
                                    "failed", summary.failed() + 1,
                                    "error.type", failure.getClass().getName()
                            )
                    );
                    // The propagated exception is diagnosed once by the centralized handler.
                })
                .collect().asList()
                .map(QuizImportResponse::from)
                .chain(result -> OperationLog.outcome(
                        Uni.createFrom().item(result),
                        result.failed() > 0 ? "partial_failure" : "completed",
                        "Quiz import completed: processed={total}, created={created}, failed={failed}",
                        Map.of(
                                "created", result.imported(),
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
