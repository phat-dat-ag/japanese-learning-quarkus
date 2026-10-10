package com.japaneselearning.quiz.player.service;

import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.common.exception.ValidationError;
import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.quiz.domain.QuizSessionStatus;
import com.japaneselearning.quiz.entity.QuizAnswer;
import com.japaneselearning.quiz.entity.QuizSession;
import com.japaneselearning.quiz.entity.QuizSessionOption;
import com.japaneselearning.quiz.entity.QuizSessionQuestion;
import com.japaneselearning.quiz.player.dto.QuizHistoryDetailResponse;
import com.japaneselearning.quiz.player.dto.QuizHistoryDetailResponse.Question;
import com.japaneselearning.quiz.player.dto.QuizHistoryListResponse;
import com.japaneselearning.quiz.player.dto.QuizHistorySummary;
import com.japaneselearning.quiz.player.dto.QuizNextQuestionResponse.Option;
import com.japaneselearning.quiz.repository.QuizAnswerRepository;
import com.japaneselearning.quiz.repository.QuizAnswerRepository.AnswerCounts;
import com.japaneselearning.quiz.repository.QuizSessionQuestionRepository;
import com.japaneselearning.quiz.repository.QuizSessionRepository;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class QuizHistoryService {
    private final QuizSessionRepository sessions;
    private final QuizSessionQuestionRepository questions;
    private final QuizAnswerRepository answers;

    public QuizHistoryService(
            QuizSessionRepository sessions,
            QuizSessionQuestionRepository questions,
            QuizAnswerRepository answers
    ) {
        this.sessions = sessions;
        this.questions = questions;
        this.answers = answers;
    }

    @WithTransaction
    public Uni<QuizHistoryListResponse> list(String subject, int page, int size) {
        validatePagination(page, size);
        return sessions.countCompletedBySubject(subject)
                .chain(total -> listSummaries(subject, page, size)
                        .map(items ->
                                new QuizHistoryListResponse(
                                        items,
                                        page,
                                        size,
                                        total,
                                        (total + size - 1) / size)
                        )
                );
    }

    private Uni<List<QuizHistorySummary>> listSummaries(String subject, int page, int size) {
        return sessions.findCompletedBySubject(subject, page, size)
                .chain(values -> {
                    List<Long> ids = values.stream().map(session -> session.id).toList();

                    return answers.countBySessions(ids)
                            .map(counts -> summaries(values, counts));
                });
    }

    private List<QuizHistorySummary> summaries(
            List<QuizSession> values,
            Map<Long, AnswerCounts> counts
    ) {
        return values.stream()
                .map(session -> {
                    AnswerCounts count = counts.getOrDefault(session.id, new AnswerCounts(0, 0));

                    return summary(session, count.correctCount());
                })
                .toList();
    }

    @WithTransaction
    public Uni<QuizHistoryDetailResponse> detail(String subject, Long id) {
        return sessions.findOwnedById(id, subject)
                .onItem()
                .ifNull()
                .failWith(this::notFound)
                .invoke(session -> {
                    if (session.status != QuizSessionStatus.COMPLETED) {
                        throw notFound();
                    }
                })
                .chain(this::loadHistory);
    }

    private Uni<QuizHistoryDetailResponse> loadHistory(QuizSession session) {
        return questions
                .findBySessionId(session.id)
                .chain(snapshots -> questions
                        .findOptionsBySessionId(session.id)
                        .chain(options ->
                                loadAnswers(session, snapshots, options)
                        )
                );
    }

    private Uni<QuizHistoryDetailResponse> loadAnswers(
            QuizSession session,
            List<QuizSessionQuestion> snapshots,
            List<QuizSessionOption> options
    ) {
        return answers.findBySessionId(session.id)
                .map(submitted -> detail(session, snapshots, options, submitted));
    }

    private QuizHistoryDetailResponse detail(
            QuizSession session,
            List<QuizSessionQuestion> snapshots,
            List<QuizSessionOption> options,
            List<QuizAnswer> submitted
    ) {
        Map<Long, List<QuizSessionOption>> byQuestion =
                options.stream().collect(Collectors.groupingBy(option -> option.sessionQuestionId));
        Map<Long, QuizAnswer> answersByQuestion =
                submitted.stream().collect(
                        Collectors.toMap(answer -> answer.sessionQuestionId, Function.identity())
                );
        List<Question> history = snapshots.stream().map(snapshot ->
                question(
                        snapshot,
                        byQuestion.getOrDefault(snapshot.id, List.of()),
                        answersByQuestion.get(snapshot.id)
                )
        ).toList();

        long correctCount = history.stream().filter(Question::correct).count();

        return new QuizHistoryDetailResponse(summary(session, correctCount), history);
    }

    private Question question(
            QuizSessionQuestion snapshot,
            List<QuizSessionOption> options,
            QuizAnswer answer
    ) {
        if (answer == null) {
            throw new IllegalStateException("Completed session snapshot has no answer");
        }

        Long correctOptionId = options.stream()
                .filter(option -> option.correct)
                .map(option -> option.id)
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException("Snapshot has no correct option")
                );

        return new Question(
                snapshot.id,
                snapshot.questionNumber,
                snapshot.sentenceReading,
                snapshot.targetStart,
                snapshot.targetLength,
                options.stream().map(option -> new Option(option.id, option.optionText)).toList(),
                answer.selectedOptionId,
                correctOptionId,
                correctOptionId.equals(answer.selectedOptionId),
                snapshot.explanationVi,
                snapshot.explanationEn,
                answer.answeredAt
        );
    }

    private QuizHistorySummary summary(QuizSession session, long correctCount) {
        // Creation filters were never persisted; do not infer them from mutable classifications.
        return new QuizHistorySummary(
                session.id,
                session.status,
                null,
                null,
                session.completedAt,
                session.questionCount,
                correctCount,
                session.questionCount - correctCount,
                correctCount
        );
    }

    private void validatePagination(int page, int size) {
        List<ValidationError> errors = new ArrayList<>();
        if (page < 0) {
            errors.add(new ValidationError("page", "Page must be greater than or equal to 0"));
        }

        if (size <= 0 || size > 100) {
            errors.add(new ValidationError("size", "Size must be between 1 and 100"));
        } else if (page >= 0 && (long) page * size > Integer.MAX_VALUE) {
            errors.add(new ValidationError("page", "Page exceeds the supported pagination range"));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(
                    "QUIZ_HISTORY_INVALID", "Invalid history pagination", errors);
        }
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("QUIZ_SESSION_NOT_FOUND", "Quiz session not found");
    }
}
