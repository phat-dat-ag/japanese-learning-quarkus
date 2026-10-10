package com.japaneselearning.quiz.service;

import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.quiz.domain.ExampleReading;
import com.japaneselearning.quiz.domain.QuestionOption;
import com.japaneselearning.quiz.domain.QuestionSnapshot;
import com.japaneselearning.quiz.domain.QuestionSource;
import com.japaneselearning.quiz.domain.QuizQuestionRules;
import com.japaneselearning.quiz.domain.QuizSessionStatus;
import com.japaneselearning.quiz.entity.QuizQuestion;
import com.japaneselearning.quiz.entity.QuizSession;
import com.japaneselearning.quiz.repository.QuizClassificationSnapshotRepository;
import com.japaneselearning.quiz.repository.QuizGameRepository;
import com.japaneselearning.quiz.repository.QuizQuestionOptionRepository;
import com.japaneselearning.quiz.repository.QuizQuestionRepository;
import com.japaneselearning.quiz.repository.QuizSessionQuestionRepository;
import com.japaneselearning.quiz.repository.QuizSessionRepository;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class QuizSessionSnapshotService {
    public static final int MAX_SESSION_QUESTIONS = 100;
    private final QuizGameRepository games;
    private final QuizQuestionRepository questions;
    private final QuizQuestionOptionRepository options;
    private final QuizSessionRepository sessions;
    private final QuizSessionQuestionRepository snapshots;
    private final QuizClassificationSnapshotRepository classifications;

    public QuizSessionSnapshotService(
            QuizQuestionRepository questions,
            QuizQuestionOptionRepository options,
            QuizSessionRepository sessions,
            QuizSessionQuestionRepository snapshots,
            QuizGameRepository games,
            QuizClassificationSnapshotRepository classifications
    ) {
        this.games = games;
        this.questions = questions;
        this.options = options;
        this.sessions = sessions;
        this.snapshots = snapshots;
        this.classifications = classifications;
    }

    @WithTransaction
    public Uni<QuizSession> create(String userSubject, List<Long> questionIds) {
        return create(userSubject, questionIds, null, null);
    }

    @WithTransaction
    public Uni<QuizSession> create(
            String userSubject,
            List<Long> questionIds,
            Long levelId,
            Long lessonId
    ) {
        if (userSubject == null
                || userSubject.isBlank()
                || userSubject.codePointCount(0, userSubject.length()) > 255
        ) {
            throw QuizQuestionRules.invalid(
                    "userSubject",
                    "A JWT subject of at most 255 code points is required"
            );
        }

        if (questionIds == null
                || questionIds.isEmpty()
                || questionIds.size() > MAX_SESSION_QUESTIONS
                || questionIds.stream().anyMatch(id -> id == null || id <= 0)
                || questionIds.stream().distinct().count() != questionIds.size()
        ) {
            throw QuizQuestionRules.invalid(
                    "questionIds",
                    "Select 1 to 100 distinct persisted questions"
            );
        }

        List<Long> selected = List.copyOf(questionIds);
        // Acquire every question before any source; sorted locks also cover shared examples.
        return Multi.createFrom().iterable(selected.stream().sorted().toList())
                .onItem().transformToUniAndConcatenate(id -> questions.findByIdForUpdate(id)
                        .onItem().ifNull().failWith(() -> new ResourceNotFoundException(
                                "QUIZ_QUESTION_NOT_FOUND", "Quiz question not found"
                        ))
                )
                .collect().asList().chain(locked -> lockSources(locked)
                        .call(() -> games.stillMatches(selected, levelId, lessonId).invoke(matches -> {
                            if (!matches) {
                                throw QuizQuestionRules.invalid(
                                        "filters",
                                        "Question bank changed during creation; retry the request"
                                );
                            }
                        }))
                        .chain(sources -> capture(selected, locked, sources)))
                .chain(values -> persistSession(userSubject, values));
    }

    private Uni<List<QuestionSnapshot>> capture(
            List<Long> selected,
            List<QuizQuestion> locked,
            Map<Long, ExampleReading> sources
    ) {
        Map<Long, QuizQuestion> byId = new HashMap<>();
        locked.forEach(question -> byId.put(question.id, question));
        return Multi.createFrom().iterable(selected)
                .onItem().transformToUniAndConcatenate(id -> options.findByQuestionId(id).map(values -> {
                    QuizQuestion question = byId.get(id);
                    List<QuestionOption> shuffled = new ArrayList<>(
                            values.stream().map(option ->
                                    new QuestionOption(option.optionText, option.correct)
                            ).toList()
                    );
                    // Sequential snapshot inserts retain this shuffle when options are later read by ID.
                    Collections.shuffle(shuffled);

                    return QuestionSnapshot.capture(
                            question,
                            sources.get(question.exampleSentenceId),
                            shuffled
                    );
                }))
                .collect().asList();
    }

    private Uni<Map<Long, ExampleReading>> lockSources(List<QuizQuestion> locked) {
        Map<Long, ExampleReading> sources = new HashMap<>();
        List<Long> ids = locked.stream()
                .filter(question -> question.sourceType == QuestionSource.EXAMPLE)
                .map(question -> question.exampleSentenceId)
                .filter(java.util.Objects::nonNull)
                .distinct().sorted().toList();

        return Multi.createFrom().iterable(ids).onItem().transformToUniAndConcatenate(id ->
                        questions.findExampleReadingForUpdate(id).invoke(reading -> sources.put(id, reading))
                )
                .collect().asList().replaceWith(sources);
    }

    private Uni<QuizSession> persistSession(String userSubject, List<QuestionSnapshot> values) {
        QuizSession session = new QuizSession();

        session.userSubject = userSubject;
        session.status = QuizSessionStatus.IN_PROGRESS;
        session.questionCount = values.size();

        return sessions.persist(session).chain(() -> Multi.createFrom().range(0, values.size())
                        .onItem().transformToUniAndConcatenate(index ->
                                snapshots.persistSnapshot(session.id, index, values.get(index))
                        )
                        .collect().asList()
                ).call(sessions::flush)
                .call(() -> classifications.capture(session.id))
                .replaceWith(session);
    }
}
