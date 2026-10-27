package com.japaneselearning.quiz.player.service;

import com.japaneselearning.common.logging.StructuredLog;
import com.japaneselearning.common.logging.LogOperation;
import com.japaneselearning.common.logging.OperationLog;
import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.common.exception.ValidationError;
import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.quiz.domain.QuizSessionStatus;
import com.japaneselearning.quiz.entity.QuizSession;
import com.japaneselearning.quiz.entity.QuizSessionQuestion;
import com.japaneselearning.quiz.player.dto.QuizGameConfigResponse;
import com.japaneselearning.quiz.player.dto.QuizNextQuestionResponse;
import com.japaneselearning.quiz.player.dto.QuizNextQuestionResponse.Option;
import com.japaneselearning.quiz.player.dto.QuizNextQuestionResponse.Question;
import com.japaneselearning.quiz.player.dto.QuizSessionCompletionResponse;
import com.japaneselearning.quiz.player.dto.QuizSessionCreateRequest;
import com.japaneselearning.quiz.player.dto.QuizSessionCreatedResponse;
import com.japaneselearning.quiz.player.dto.QuizSessionResponse;
import com.japaneselearning.quiz.repository.QuizAnswerRepository;
import com.japaneselearning.quiz.repository.QuizGameRepository;
import com.japaneselearning.quiz.repository.QuizSessionQuestionRepository;
import com.japaneselearning.quiz.repository.QuizSessionRepository;
import com.japaneselearning.quiz.service.QuizSessionSnapshotService;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PessimisticLockException;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.LockAcquisitionException;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class QuizGameService {
    private static final Logger LOG = Logger.getLogger(QuizGameService.class);

    private final QuizGameRepository games;
    private final QuizSessionSnapshotService snapshots;
    private final QuizSessionRepository sessions;
    private final QuizSessionQuestionRepository sessionQuestions;
    private final QuizAnswerRepository answers;

    public QuizGameService(
            QuizGameRepository games,
            QuizSessionSnapshotService snapshots,
            QuizSessionRepository sessions,
            QuizSessionQuestionRepository sessionQuestions,
            QuizAnswerRepository answers
    ) {
        this.games = games;
        this.snapshots = snapshots;
        this.sessions = sessions;
        this.sessionQuestions = sessionQuestions;
        this.answers = answers;
    }

    @WithTransaction
    @LogOperation(
            value = "quiz.configuration",
            description = "Quiz configuration retrieval",
            successMessage = "Successfully retrieved quiz configuration with {total} playable questions"
    )
    public Uni<QuizGameConfigResponse> configuration() {
        return games.configuration(QuizSessionSnapshotService.MAX_SESSION_QUESTIONS)
                .chain(result -> OperationLog.outcome(
                        Uni.createFrom().item(result),
                        "completed",
                        Map.of(
                                "total", result.totalQuestions(),
                                "count", result.levels().size() + result.lessons().size()
                        )
                ));
    }

    @WithTransaction
    @LogOperation(
            value = "quiz.session.creation",
            description = "Quiz session creation",
            successMessage = "Successfully started quiz session with sessionId={sessionId} and {count} questions"
    )
    public Uni<QuizSessionCreatedResponse> create(String subject, QuizSessionCreateRequest request) {
        return validateFilters(request.levelId(), request.lessonId())
                .chain(() -> games.selectRandom(request.levelId(), request.lessonId(), request.questionCount()))
                .chain(ids -> {
                    StructuredLog.event(
                            LOG,
                            Logger.Level.DEBUG,
                            "quiz.session.selection",
                            "Selected " + ids.size()
                                    + " playable quiz questions for a request of "
                                    + request.questionCount(),
                            Map.of(
                                    "operation", "quiz.session.creation",
                                    "count", ids.size(),
                                    "total", request.questionCount(),
                                    "reason", ids.size() < request.questionCount()
                                            ? "insufficient"
                                            : "eligible"
                            )
                    );

                    if (ids.size() < request.questionCount()) {
                        throw new ConflictException(
                                "QUIZ_INSUFFICIENT_QUESTIONS",
                                "Requested " + request.questionCount()
                                        + " questions but only " + ids.size()
                                        + " playable questions match the filters"
                        );
                    }

                    return snapshots.create(subject, ids, request.levelId(), request.lessonId())
                            .onFailure(failure -> failure instanceof ValidationException
                                    || failure instanceof ResourceNotFoundException
                            )
                            .transform(failure -> changed());
                })
                .chain(session -> OperationLog.outcome(Uni.createFrom().item(
                                new QuizSessionCreatedResponse(
                                        session.id, session.status, session.questionCount, session.createdAt
                                )),
                        "completed",
                        Map.of("sessionId", session.id, "count", session.questionCount)
                ))
                .onFailure(failure -> failure instanceof ConstraintViolationException
                        || failure instanceof LockAcquisitionException
                        || failure instanceof PessimisticLockException)
                .transform(failure -> changed());
    }

    @WithTransaction
    @LogOperation(
            value = "quiz.session.detail",
            id = "sessionId",
            description = "Quiz session retrieval",
            successMessage = "Successfully retrieved sessionId={sessionId} with {count} answered questions out of {total}"
    )
    public Uni<QuizSessionResponse> session(String subject, Long id) {
        return ownedSession(subject, id)
                .chain(session -> answers.countBySession(session.id)
                        .map(counts -> new QuizSessionResponse(
                                session.id,
                                session.status,
                                session.questionCount,
                                counts.answeredCount(),
                                counts.correctCount()
                        ))
                ).chain(result -> OperationLog.outcome(
                        Uni.createFrom().item(result),
                        "completed",
                        Map.of(
                                "count", result.answeredCount(),
                                "total", result.questionCount()
                        )
                ));
    }

    @WithTransaction
    @LogOperation(
            value = "quiz.session.next",
            id = "sessionId",
            description = "Next quiz question retrieval"
    )
    public Uni<QuizNextQuestionResponse> next(String subject, Long id) {
        return ownedSession(subject, id)
                .chain(session -> nextQuestion(session)
                        .map(question -> new QuizNextQuestionResponse(
                                session.id,
                                session.status,
                                question
                        ))
                ).chain(result -> OperationLog.outcome(
                        Uni.createFrom().item(result),
                        "completed",
                        result.question() == null
                                ? "No next question is available for sessionId={sessionId}; the session is finished or all questions are answered"
                                : "Successfully retrieved the next unanswered question for sessionId={sessionId}",
                        Map.of("count", result.question() == null ? 0 : 1)
                ));
    }

    @WithTransaction
    @LogOperation(
            value = "quiz.session.finish",
            id = "sessionId",
            description = "Quiz session completion",
            successMessage = "Successfully completed quiz session with sessionId={sessionId}"
    )
    public Uni<QuizSessionCompletionResponse> finish(String subject, Long id) {
        // Share the answer submission lock before reading status or counts.
        return sessions.findOwnedByIdForUpdate(id, subject)
                .onItem()
                .ifNull()
                .failWith(() -> new ResourceNotFoundException(
                        "QUIZ_SESSION_NOT_FOUND", "Quiz session not found"
                ))
                .chain(session -> {
                    if (session.status != QuizSessionStatus.IN_PROGRESS) {
                        throw finishConflict("Session no longer accepts completion");
                    }

                    return answers.countBySession(session.id)
                            .chain(counts -> complete(session, counts));
                })
                .onFailure(failure ->
                        failure instanceof LockAcquisitionException
                                || failure instanceof PessimisticLockException
                )
                .transform(
                        failure -> finishConflict("Concurrent session update; retry the request")
                );
    }

    private Uni<QuizSessionCompletionResponse> complete(
            QuizSession session,
            QuizAnswerRepository.AnswerCounts counts
    ) {
        if (counts.answeredCount() != session.questionCount) {
            throw finishConflict("All session questions must be answered before completion");
        }

        StructuredLog.event(
                LOG,
                Logger.Level.DEBUG,
                "quiz.session.score",
                "Calculated final quiz score: "
                        + counts.correctCount()
                        + " correct answers out of "
                        + counts.answeredCount(),
                Map.of(
                        "operation", "quiz.session.finish",
                        "sessionId", session.id,
                        "count", counts.correctCount(),
                        "total", counts.answeredCount()
                )
        );

        session.status = QuizSessionStatus.COMPLETED;
        session.completedAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);

        return sessions.flush().replaceWith(() -> new QuizSessionCompletionResponse(
                session.id,
                session.status,
                session.questionCount,
                counts.correctCount(),
                counts.answeredCount() - counts.correctCount(),
                counts.correctCount(),
                session.completedAt
        ));
    }

    private ConflictException finishConflict(String message) {
        StructuredLog.event(
                LOG,
                Logger.Level.DEBUG,
                "quiz.session.finish.validation_failed",
                message,
                Map.of(
                        "operation", "quiz.session.finish",
                        "reason", "completion_not_allowed"
                )
        );

        return new ConflictException("QUIZ_FINISH_CONFLICT", message);
    }

    private Uni<Question> nextQuestion(QuizSession session) {
        if (session.status != QuizSessionStatus.IN_PROGRESS) {
            return Uni.createFrom().nullItem();
        }

        return sessionQuestions
                .findNextUnanswered(session.id)
                .chain(this::snapshotQuestion);
    }

    private Uni<Question> snapshotQuestion(QuizSessionQuestion question) {
        if (question == null) {
            return Uni.createFrom().nullItem();
        }

        return sessionQuestions
                .findOptions(question.id)
                .map(options -> {
                    List<Option> choices = options.stream()
                            .map(option ->
                                    new Option(option.id, option.optionText)
                            )
                            .toList();

                    return new Question(
                            question.id,
                            question.questionNumber,
                            question.sentenceReading,
                            question.targetStart,
                            question.targetLength,
                            choices
                    );
                });
    }

    private Uni<QuizSession> ownedSession(String subject, Long id) {
        return sessions.findOwnedById(id, subject)
                .onItem()
                .ifNull()
                .failWith(() -> new ResourceNotFoundException(
                        "QUIZ_SESSION_NOT_FOUND",
                        "Quiz session not found"
                ));
    }

    private Uni<Void> validateFilters(Long levelId, Long lessonId) {
        Uni<Void> level = levelId == null
                ? Uni.createFrom().voidItem()
                : games.findLevelForShare(levelId)
                .onItem()
                .ifNull()
                .failWith(() -> invalid("levelId", "JLPT level does not exist"))
                .replaceWithVoid();

        return level.chain(() -> lessonId == null ? Uni.createFrom().voidItem()
                : games.findLessonForShare(lessonId).onItem().ifNull()
                .failWith(() -> invalid("lessonId", "Lesson does not exist"))
                .invoke(lesson -> {
                    if (levelId != null && !levelId.equals(lesson.levelId)) {
                        throw invalid("lessonId", "Lesson must belong to the selected JLPT level");
                    }
                }).replaceWithVoid());
    }

    private ValidationException invalid(String field, String message) {
        StructuredLog.event(
                LOG,
                Logger.Level.DEBUG,
                "quiz.session.filters_invalid",
                message,
                Map.of("operation", "quiz.session.creation", "reason", field)
        );

        return new ValidationException("QUIZ_GAME_INVALID", "Invalid game filters",
                List.of(new ValidationError(field, message)));
    }

    private ConflictException changed() {
        return new ConflictException(
                "QUIZ_GAME_CONFLICT",
                "Question bank changed during creation; retry the request"
        );
    }
}
