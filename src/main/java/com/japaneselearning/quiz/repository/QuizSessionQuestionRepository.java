package com.japaneselearning.quiz.repository;

import com.japaneselearning.quiz.domain.QuestionSnapshot;
import com.japaneselearning.quiz.entity.QuizSessionOption;
import com.japaneselearning.quiz.entity.QuizSessionQuestion;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@ApplicationScoped
public class QuizSessionQuestionRepository implements PanacheRepository<QuizSessionQuestion> {
    // Caller owns the transaction for session, snapshots, and all option inserts.
    public Uni<QuizSessionQuestion> persistSnapshot(
            Long sessionId, int questionNumber, QuestionSnapshot snapshot) {
        return Panache.getSession()
                .chain(
                        session -> {
                            if (session.currentTransaction() == null) {
                                return Uni.createFrom()
                                        .failure(
                                                new IllegalStateException(
                                                        "Snapshot persistence requires a transaction"
                                                )
                                        );
                            }

                            QuizSessionQuestion question = new QuizSessionQuestion();

                            question.sessionId = sessionId;
                            question.questionId = snapshot.questionId();
                            question.questionVersion = snapshot.questionVersion();
                            question.questionNumber = questionNumber;
                            question.sourceType = snapshot.source();
                            question.sourceExampleId = snapshot.sourceExampleId();
                            question.sentenceReading = snapshot.sentenceReading();
                            question.targetStart = snapshot.target().start();
                            question.targetLength = snapshot.target().length();
                            question.targetReading = snapshot.target().reading();
                            question.explanationVi = snapshot.explanationVi();
                            question.explanationEn = snapshot.explanationEn();
                            question.createdAt = LocalDateTime.now(ZoneOffset.UTC);

                            return persist(question)
                                    .chain(
                                            () ->
                                                    Multi.createFrom()
                                                            .iterable(snapshot.options())
                                                            .onItem()
                                                            .transformToUniAndConcatenate(
                                                                    value -> {
                                                                        QuizSessionOption option =
                                                                                new QuizSessionOption();
                                                                        option.sessionQuestionId =
                                                                                question.id;
                                                                        option.optionText =
                                                                                value.text();
                                                                        option.correct =
                                                                                value.correct();
                                                                        return session.persist(
                                                                                option);
                                                                    }
                                                            )
                                                            .collect()
                                                            .asList()
                                    ).replaceWith(question);
                        });
    }

    public Uni<QuizSessionQuestion> findNextUnanswered(Long sessionId) {
        return find(
                """
                                from QuizSessionQuestion q where q.sessionId = ?1 and not exists (
                                    select 1 from QuizAnswer a where a.sessionQuestionId = q.id
                                ) order by q.questionNumber
                        """,
                sessionId
        ).firstResult();
    }

    public Uni<List<QuizSessionQuestion>> findBySessionId(Long sessionId) {
        return list("sessionId = ?1 order by questionNumber", sessionId);
    }

    public Uni<List<QuizSessionOption>> findOptions(Long sessionQuestionId) {
        return Panache.getSession()
                .chain(
                        session ->
                                session.createQuery(
                                                "from QuizSessionOption where sessionQuestionId = :id order by id",
                                                QuizSessionOption.class
                                        )
                                        .setParameter("id", sessionQuestionId)
                                        .getResultList()
                );
    }

    public Uni<List<QuizSessionOption>> findOptionsBySessionId(Long sessionId) {
        return Panache.getSession()
                .chain(session -> session.createQuery(
                                        """
                                                select o from QuizSessionOption o
                                                join QuizSessionQuestion q on q.id = o.sessionQuestionId
                                                where q.sessionId = :id order by q.questionNumber, o.id
                                                """,
                                        QuizSessionOption.class
                                )
                                .setParameter("id", sessionId)
                                .getResultList()
                );
    }
}
