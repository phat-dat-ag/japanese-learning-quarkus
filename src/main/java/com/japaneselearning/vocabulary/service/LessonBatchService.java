package com.japaneselearning.vocabulary.service;

import com.japaneselearning.common.exception.handler.BatchItemErrors;
import com.japaneselearning.common.exception.handler.HttpErrors;
import com.japaneselearning.vocabulary.dto.LessonBatchResponse;
import com.japaneselearning.vocabulary.dto.LessonBatchResult;
import com.japaneselearning.vocabulary.dto.LessonWriteRequest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Validator;

import java.util.List;

@ApplicationScoped
public class LessonBatchService {
    public static final int MAX_BATCH_SIZE = 100;

    private final LessonService lessons;
    private final Validator validator;
    private final BatchItemErrors errors;

    public LessonBatchService(LessonService lessons, Validator validator, BatchItemErrors errors) {
        this.lessons = lessons;
        this.validator = validator;
        this.errors = errors;
    }

    // Do not add an outer session/transaction: each CDI createLesson call owns and closes its own.
    public Uni<LessonBatchResponse> createLessons(List<LessonWriteRequest> requests) {
        return Multi.createFrom().range(0, requests.size())
                .onItem().transformToUniAndConcatenate(
                        index -> createItem(index, requests.get(index))
                )
                .collect().asList()
                .map(LessonBatchResponse::from);
    }

    private Uni<LessonBatchResult> createItem(
            int index,
            LessonWriteRequest request
    ) {
        return Uni.createFrom().deferred(() -> {
                    if (request == null || !validator.validate(request).isEmpty()) {
                        return Uni.createFrom().item(
                                new LessonBatchResult(index, false, null, HttpErrors.forStatus(400))
                        );
                    }

                    return lessons.createLesson(request).map(
                            lesson -> new LessonBatchResult(index, true, lesson, null)
                    );
                })
                // Recovery runs after the intercepted Uni has committed/rolled back and closed its session.
                .onFailure()
                .recoverWithItem(failure ->
                        new LessonBatchResult(index, false, null, errors.from(failure))
                );
    }
}
