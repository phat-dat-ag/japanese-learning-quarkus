package com.japaneselearning.vocabulary.service;

import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.common.exception.handler.BatchItemErrors;
import com.japaneselearning.common.logging.StructuredLog;
import com.japaneselearning.vocabulary.dto.content.LessonContentImportItem;
import com.japaneselearning.vocabulary.dto.content.LessonContentImportResponse;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import org.hibernate.exception.LockAcquisitionException;
import jakarta.persistence.PessimisticLockException;
import jakarta.persistence.LockTimeoutException;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class LessonContentImportService {
    private static final Logger LOG = Logger.getLogger(LessonContentImportService.class);

    private final LessonContentImportPreflight preflight;
    private final LessonContentImportItemService writer;
    private final BatchItemErrors errors;

    public LessonContentImportService(
            LessonContentImportPreflight preflight,
            LessonContentImportItemService writer,
            BatchItemErrors errors
    ) {
        this.preflight = preflight;
        this.writer = writer;
        this.errors = errors;
    }

    // No outer session or transaction: validation finishes before independently committed items begin.
    public Uni<LessonContentImportResponse> importLessons(
            List<LessonContentImportItem> items,
            Mode mode
    ) {
        return preflight.validate(items)
                .chain(prepared -> Multi.createFrom().iterable(prepared)
                        .onItem()
                        .transformToUniAndConcatenate(item -> writer.apply(item, mode)
                                .onFailure(failure -> failure instanceof LockAcquisitionException
                                        || failure instanceof PessimisticLockException
                                        || failure instanceof LockTimeoutException
                                )
                                .transform(failure -> new ConflictException(
                                        "LESSON_CONFLICT",
                                        "Concurrent import changed this lesson; retry after reading its current state"
                                ))
                                .onFailure().recoverWithItem(failure -> new LessonContentImportResponse.Item(
                                        item.index(),
                                        item.item().level().trim(),
                                        item.item().lessonNumber(),
                                        LessonContentImportResponse.Status.FAILED,
                                        null,
                                        errors.from(failure)
                                ))
                        )
                        .collect().asList().map(LessonContentImportResponse::from)
                )
                .invoke(report -> StructuredLog.event(
                        LOG,
                        report.failed() == 0 ? Logger.Level.INFO : Logger.Level.WARN,
                        "lesson.content.import.completed",
                        "Completed grammar lesson import with independently committed item results",
                        Map.of(
                                "total", report.total(),
                                "created", report.created(),
                                "updated", report.updated(),
                                "skipped", report.skipped(),
                                "failed", report.failed()
                        )
                ));
    }
}
