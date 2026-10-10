package com.japaneselearning.quiz.admin.service;

import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.quiz.admin.dto.QuizOptionResponse;
import com.japaneselearning.quiz.admin.dto.QuizQuestionFilter;
import com.japaneselearning.quiz.admin.dto.QuizQuestionListResponse;
import com.japaneselearning.quiz.admin.dto.QuizQuestionResponse;
import com.japaneselearning.quiz.admin.repository.QuizAdminRepository;
import com.japaneselearning.quiz.domain.ExampleReading;
import com.japaneselearning.quiz.domain.QuestionSource;
import com.japaneselearning.quiz.domain.QuestionStatus;
import com.japaneselearning.quiz.domain.QuizQuestionRules;
import com.japaneselearning.quiz.entity.QuizQuestion;
import com.japaneselearning.quiz.entity.QuizQuestionLesson;
import com.japaneselearning.quiz.entity.QuizQuestionLevel;
import com.japaneselearning.quiz.entity.QuizQuestionOption;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@ApplicationScoped
public class QuizAdminQueryService {
    private final QuizAdminRepository repository;

    public QuizAdminQueryService(QuizAdminRepository repository) {
        this.repository = repository;
    }

    @WithTransaction
    public Uni<QuizQuestionResponse> detail(Long id) {
        return repository.findById(id).onItem().ifNull().failWith(
                        () -> new ResourceNotFoundException(
                                "QUIZ_QUESTION_NOT_FOUND", "Quiz question not found"
                        )
                )
                .chain(question -> responses(List.of(question)))
                .map(values -> values.get(0));
    }

    @WithTransaction
    public Uni<QuizQuestionListResponse> list(QuizQuestionFilter filter) {
        if (filter.page() < 0
                || filter.size() < 1
                || filter.size() > 100
                || (long) filter.page() * filter.size() + filter.size() > Integer.MAX_VALUE
        ) {
            throw QuizQuestionRules.invalid(
                    "page",
                    "Page must be nonnegative, size 1-100, and range fit int32"
            );
        }

        return repository.search(filter).chain(this::responses)
                .chain(items -> repository.countMatching(filter).map(total ->
                        new QuizQuestionListResponse(
                                items, filter.page(), filter.size(), total,
                                (total + filter.size() - 1) / filter.size()
                        )
                ));
    }

    private Uni<List<QuizQuestionResponse>> responses(List<QuizQuestion> questions) {
        if (questions.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }

        List<Long> ids = questions.stream().map(question -> question.id).toList();

        return repository.options(ids).chain(options -> repository.levels(ids)
                .chain(levels -> repository.lessons(ids)
                        .chain(lessons -> repository.sources(ids)
                                .map(rows -> assemble(questions, options, levels, lessons, rows))
                        )
                )
        );
    }

    private List<QuizQuestionResponse> assemble(
            List<QuizQuestion> questions,
            List<QuizQuestionOption> options,
            List<QuizQuestionLevel> levels,
            List<QuizQuestionLesson> lessons,
            List<Object[]> rows
    ) {
        Map<Long, ExampleReading> sources = new HashMap<>();

        rows.forEach(row -> sources.put(
                ((Number) row[0]).longValue(),
                new ExampleReading((String) row[1], (String) row[2])
        ));

        return questions.stream().map(question -> response(
                question,
                sources.get(question.id),
                levels.stream()
                        .filter(level -> level.questionId.equals(question.id))
                        .map(level -> level.levelId).toList(),
                lessons.stream()
                        .filter(lesson -> lesson.questionId.equals(question.id))
                        .map(lesson -> lesson.lessonId).toList(),
                options.stream()
                        .filter(option -> option.questionId.equals(question.id))
                        .map(option -> new QuizOptionResponse(
                                option.id, option.optionText, option.correct
                        ))
                        .toList()
        )).toList();
    }

    private QuizQuestionResponse response(
            QuizQuestion question,
            ExampleReading source,
            List<Long> levelIds,
            List<Long> lessonIds,
            List<QuizOptionResponse> options
    ) {
        boolean invalidated = question.sourceType == QuestionSource.EXAMPLE
                && (
                source == null
                        || question.validatedExampleFingerprint == null
                        || !Objects.equals(question.validatedExampleFingerprint, source.fingerprint())
        );

        String reading = question.sentenceReading;

        if (question.sourceType == QuestionSource.EXAMPLE) {
            reading = source == null ? null : source.reading();
        }

        return new QuizQuestionResponse(
                question.id,
                question.version,
                question.sourceType,
                question.status,
                question.exampleSentenceId,
                question.vocabularyId, reading,
                question.targetStart,
                question.targetLength,
                question.targetReading,
                question.explanationVi,
                question.explanationEn,
                invalidated,
                question.status == QuestionStatus.PUBLISHED && !invalidated,
                levelIds,
                lessonIds,
                options,
                question.createdAt,
                question.updatedAt
        );
    }
}
