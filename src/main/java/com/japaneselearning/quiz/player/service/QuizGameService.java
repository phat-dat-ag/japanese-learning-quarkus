package com.japaneselearning.quiz.player.service;

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

import java.util.List;

@ApplicationScoped
public class QuizGameService {
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
    public Uni<QuizGameConfigResponse> configuration() {
        return games.configuration(QuizSessionSnapshotService.MAX_SESSION_QUESTIONS);
    }

    @WithTransaction
    public Uni<QuizSessionCreatedResponse> create(String subject, QuizSessionCreateRequest request) {
        return validateFilters(request.levelId(), request.lessonId())
                .chain(() -> games.selectRandom(request.levelId(), request.lessonId(), request.questionCount()))
                .chain(ids -> {
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
                .map(session -> new QuizSessionCreatedResponse(
                        session.id, session.status, session.questionCount, session.createdAt
                ))
                .onFailure(failure -> failure instanceof ConstraintViolationException
                        || failure instanceof LockAcquisitionException
                        || failure instanceof PessimisticLockException)
                .transform(failure -> changed());
    }

    @WithTransaction
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
                );
    }

    @WithTransaction
    public Uni<QuizNextQuestionResponse> next(String subject, Long id) {
        return ownedSession(subject, id)
                .chain(session -> nextQuestion(session)
                        .map(question -> new QuizNextQuestionResponse(
                                session.id,
                                session.status,
                                question
                        ))
                );
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
