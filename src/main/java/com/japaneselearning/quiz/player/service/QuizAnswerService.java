package com.japaneselearning.quiz.player.service;

import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.common.exception.ValidationError;
import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.quiz.domain.QuizSessionStatus;
import com.japaneselearning.quiz.entity.QuizAnswer;
import com.japaneselearning.quiz.entity.QuizSession;
import com.japaneselearning.quiz.entity.QuizSessionOption;
import com.japaneselearning.quiz.entity.QuizSessionQuestion;
import com.japaneselearning.quiz.player.dto.QuizAnswerRequest;
import com.japaneselearning.quiz.player.dto.QuizAnswerResponse;
import com.japaneselearning.quiz.repository.QuizAnswerRepository;
import com.japaneselearning.quiz.repository.QuizSessionQuestionRepository;
import com.japaneselearning.quiz.repository.QuizSessionRepository;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PessimisticLockException;

import org.hibernate.exception.LockAcquisitionException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@ApplicationScoped
public class QuizAnswerService {
    private final QuizSessionRepository sessions;
    private final QuizSessionQuestionRepository questions;
    private final QuizAnswerRepository answers;

    public QuizAnswerService(
            QuizSessionRepository sessions,
            QuizSessionQuestionRepository questions,
            QuizAnswerRepository answers
    ) {
        this.sessions = sessions;
        this.questions = questions;
        this.answers = answers;
    }

    @WithTransaction
    public Uni<QuizAnswerResponse> submit(String subject, Long id, QuizAnswerRequest request) {
        // Lock before any snapshot/count reads so competing submissions see the committed answer.
        return sessions.findOwnedByIdForUpdate(id, subject)
                .onItem()
                .ifNull()
                .failWith(() -> new ResourceNotFoundException(
                        "QUIZ_SESSION_NOT_FOUND", "Quiz session not found"
                ))
                .chain(session -> nextQuestion(session, request)
                        .chain(question -> questions
                                .findOptions(question.id)
                                .chain(options ->
                                        persistAnswer(
                                                session,
                                                question,
                                                options,
                                                request
                                        )
                                )
                        )
                )
                .onFailure(
                        failure -> failure instanceof LockAcquisitionException
                                || failure instanceof PessimisticLockException
                )
                .transform(failure -> conflict("Concurrent session update; retry the request"));
    }

    private Uni<QuizSessionQuestion> nextQuestion(QuizSession session, QuizAnswerRequest request) {
        if (session.status != QuizSessionStatus.IN_PROGRESS) {
            throw conflict("Session no longer accepts answers");
        }

        return questions
                .findNextUnanswered(session.id)
                .onItem()
                .ifNull()
                .failWith(() -> conflict("All session questions have been answered"))
                .invoke(question -> {
                    if (!question.id.equals(request.sessionQuestionId())) {
                        throw conflict(
                                "Only the next unanswered session question can be answered"
                        );
                    }
                });
    }

    private Uni<QuizAnswerResponse> persistAnswer(
            QuizSession session,
            QuizSessionQuestion question,
            List<QuizSessionOption> options,
            QuizAnswerRequest request
    ) {
        QuizSessionOption selected = options.stream()
                .filter(option -> option.id.equals(request.selectedOptionId()))
                .findFirst()
                .orElseThrow(() -> new ValidationException(
                        "QUIZ_ANSWER_INVALID",
                        "Invalid answer",
                        List.of(new ValidationError(
                                "selectedOptionId",
                                "Selected option must belong to the current session question"
                        ))
                ));

        Long correctOptionId = options.stream()
                .filter(option -> option.correct)
                .map(option -> option.id)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Snapshot has no correct option"));

        QuizAnswer answer = new QuizAnswer();

        answer.sessionQuestionId = question.id;
        answer.selectedOptionId = selected.id;
        answer.answeredAt = LocalDateTime.now(ZoneOffset.UTC);
        // Score and progress are derived from answers, as in the existing session GET.
        return answers.persistAndFlush(answer)
                .chain(() -> answers.countBySession(session.id))
                .map(counts -> new QuizAnswerResponse(
                        question.id,
                        selected.correct,
                        correctOptionId,
                        question.explanationVi,
                        question.explanationEn,
                        counts.correctCount(),
                        counts.answeredCount(),
                        session.questionCount - counts.answeredCount()
                ));
    }

    private ConflictException conflict(String message) {
        return new ConflictException("QUIZ_ANSWER_CONFLICT", message);
    }
}
