package com.japaneselearning.quiz;

import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.quiz.domain.*;
import com.japaneselearning.quiz.entity.*;
import com.japaneselearning.quiz.repository.*;
import com.japaneselearning.quiz.service.QuizPublicationService;
import com.japaneselearning.quiz.service.QuizSessionSnapshotService;
import com.japaneselearning.vocabulary.admin.service.VocabularyExampleEditService;
import com.japaneselearning.vocabulary.admin.dto.VocabularyExampleUpdateRequest;
import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.security.JwksTestResource;
import com.japaneselearning.security.VocabularyMysqlTestProfile;
import com.japaneselearning.vocabulary.entity.ExampleSentence;
import com.japaneselearning.vocabulary.entity.Lesson;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.vertx.VertxContextSupport;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(VocabularyMysqlTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
@EnabledIfSystemProperty(named = "vocabulary.mysql.tests", matches = "true")
class QuizFoundationMysqlTest {
    private static final String READING = "\u304c\u3063\u3053\u3046";
    @Inject
    QuizQuestionRepository questions;
    @Inject
    QuizQuestionOptionRepository options;
    @Inject
    QuizQuestionLevelRepository levels;
    @Inject
    QuizQuestionLessonRepository lessons;
    @Inject
    QuizSessionRepository sessions;
    @Inject
    QuizSessionQuestionRepository snapshots;
    @Inject
    QuizAnswerRepository answers;
    @Inject
    QuizUserProgressRepository progress;
    @Inject
    QuizPublicationService publication;
    @Inject
    MySQLPool pool;
    @Inject
    QuizSessionSnapshotService sessionCreation;
    @Inject
    VocabularyExampleEditService exampleEdits;

    @Test
    void publishesStandaloneCustomAndFiltersByExplicitLevelAndLesson() throws Throwable {
        long id = draft(null, 4);
        long levelId = number("SELECT id FROM jlpt_levels WHERE code='N5'");
        long otherLevel = number("SELECT id FROM jlpt_levels WHERE code='N4'");
        long lessonId = tx(() -> {
            Lesson lesson = new Lesson();
            lesson.levelId = levelId;
            lesson.lessonNumber = ThreadLocalRandom.current().nextInt(100000, 1000000000);
            lesson.displayOrder = lesson.lessonNumber;
            lesson.title = "Quiz test";
            lesson.createdAt = LocalDateTime.now();
            lesson.updatedAt = lesson.createdAt;
            return Panache.getSession().chain(session -> session.persist(lesson)).replaceWith(() -> lesson.id);
        });
        tx(() -> {
            QuizQuestionLevel level = new QuizQuestionLevel();
            level.questionId = id;
            level.levelId = levelId;
            QuizQuestionLesson lesson = new QuizQuestionLesson();
            lesson.questionId = id;
            lesson.lessonId = lessonId;
            return levels.persist(level).chain(() -> lessons.persist(lesson));
        });
        run(() -> publication.publish(id));
        assertTrue(eligible(levelId, null).contains(id));
        assertTrue(eligible(null, lessonId).contains(id));
        assertTrue(eligible(levelId, lessonId).contains(id));
        assertFalse(eligible(otherLevel, lessonId).contains(id));
        assertEquals(0, number("SELECT COUNT(*) FROM quiz_questions WHERE id=" + id + " AND vocabulary_id IS NOT NULL"));
    }

    @Test
    void validatesPublicationAndDemotesBeforeSupportedEdits() throws Throwable {
        long id = draft(null, 3);
        assertThrows(ValidationException.class, () -> run(() -> publication.publish(id)));
        assertEquals(1, number("SELECT COUNT(*) FROM quiz_questions WHERE id=" + id + " AND status='DRAFT'"));
        tx(() -> addOption(id, "fourth", false));
        run(() -> publication.publish(id));
        run(() -> publication.prepareForEdit(id));
        sql("UPDATE quiz_questions SET explanation_en='changed' WHERE id=" + id);
        assertFalse(eligible(null, null).contains(id));
        run(() -> publication.publish(id));
        assertTrue(eligible(null, null).contains(id));
    }

    @Test
    void sourceChangesAndDeletionExcludeNewGamesButRetainSnapshots() throws Throwable {
        long exampleId = tx(() -> {
            ExampleSentence sentence = new ExampleSentence();
            sentence.japaneseText = "source";
            sentence.japaneseReading = READING;
            sentence.meaningVi = "test";
            sentence.meaningEn = "test";
            return Panache.getSession().chain(session -> session.persist(sentence)).replaceWith(() -> sentence.id);
        });
        long id = draft(exampleId, 4);
        run(() -> publication.publish(id));
        var saved = saveSnapshot(id, "quiz-source-user");
        assertTrue(eligible(null, null).contains(id));
        sql("UPDATE example_sentences SET japanese_reading=CONCAT(japanese_reading,'.') WHERE id=" + exampleId);
        assertFalse(eligible(null, null).contains(id));
        sql("UPDATE example_sentences SET japanese_reading=LEFT(japanese_reading,4) WHERE id=" + exampleId);
        assertFalse(eligible(null, null).contains(id), "Reverting content must not silently revalidate it");
        run(() -> publication.prepareForEdit(id));
        run(() -> publication.publish(id));
        assertTrue(eligible(null, null).contains(id));
        sql("DELETE FROM example_sentences WHERE id=" + exampleId);
        assertFalse(eligible(null, null).contains(id));
        assertEquals(1, number("SELECT COUNT(*) FROM quiz_questions WHERE id=" + id + " AND example_sentence_id IS NULL"));
        var snapshot = tx(() -> snapshots.findById(saved.id));
        assertEquals(READING, snapshot.sentenceReading);
        assertEquals(exampleId, snapshot.sourceExampleId);
        assertEquals(4, tx(() -> snapshots.findOptions(saved.id)).size());
    }

    @Test
    void snapshotsAndAnswersRemainIndependentAndRejectCrossQuestionOptions() throws Throwable {
        long id = draft(null, 4);
        run(() -> publication.publish(id));
        var first = saveSnapshot(id, "QuizUser");
        var second = saveSnapshot(id, "quizuser");
        var firstOptions = tx(() -> snapshots.findOptions(first.id));
        var secondOptions = tx(() -> snapshots.findOptions(second.id));
        assertNull(tx(() -> sessions.findOwnedByIdForUpdate(first.sessionId, "quizuser")));
        assertThrows(RuntimeException.class, () -> sql("INSERT INTO quiz_answers(session_question_id,selected_option_id) VALUES ("
                + first.id + "," + secondOptions.get(0).id + ")"));
        tx(() -> {
            QuizAnswer answer = new QuizAnswer();
            answer.sessionQuestionId = first.id;
            answer.selectedOptionId = firstOptions.get(0).id;
            answer.answeredAt = LocalDateTime.now();
            return answers.persist(answer);
        });
        assertThrows(RuntimeException.class, () -> sql("INSERT INTO quiz_answers(session_question_id,selected_option_id) VALUES ("
                + first.id + "," + firstOptions.get(0).id + ")"));
        tx(() -> snapshots.findById(first.id).invoke(value -> value.explanationEn = "must not persist"));
        tx(() -> snapshots.findOptions(first.id).invoke(values -> values.get(0).optionText = "must not persist"));
        tx(() -> answers.findById(first.id).invoke(value -> value.selectedOptionId = firstOptions.get(1).id));
        assertNull(tx(() -> snapshots.findById(first.id)).explanationEn);
        assertEquals(firstOptions.stream().map(o -> o.optionText).collect(java.util.stream.Collectors.toSet()),
                tx(() -> snapshots.findOptions(first.id)).stream().map(o -> o.optionText).collect(java.util.stream.Collectors.toSet()));
        assertEquals(firstOptions.get(0).id, tx(() -> answers.findById(first.id)).selectedOptionId);
        run(() -> publication.prepareForEdit(id));
        sql("UPDATE quiz_question_options SET option_text='edited' WHERE question_id=" + id + " AND is_correct=1");
        run(() -> publication.publish(id));
        assertEquals(firstOptions.stream().map(o -> o.optionText).collect(java.util.stream.Collectors.toSet()),
                tx(() -> snapshots.findOptions(first.id)).stream().map(o -> o.optionText).collect(java.util.stream.Collectors.toSet()));
        sql("DELETE FROM quiz_questions WHERE id=" + id);
        assertEquals(1, number("SELECT COUNT(*) FROM quiz_answers WHERE session_question_id=" + first.id));
        assertEquals(1, number("SELECT COUNT(*) FROM quiz_session_questions WHERE id=" + first.id + " AND question_id IS NULL"));
    }

    @Test
    void enforcesProgressIdentityCountsAndOptimisticVersioning() throws Throwable {
        long id = draft(null, 4);
        String subject = UUID.randomUUID().toString();
        long progressId = tx(() -> {
            QuizUserProgress value = new QuizUserProgress();
            value.questionId = id;
            value.userSubject = subject;
            return progress.persist(value).replaceWith(() -> value.id);
        });
        tx(() -> progress.findForUpdate(subject, id).invoke(value -> {
            value.attempts = 1;
            value.correctAnswers = 1;
            value.lastAnsweredAt = LocalDateTime.now();
        }));
        assertEquals(1, number("SELECT version FROM quiz_user_progress WHERE id=" + progressId));
        assertThrows(RuntimeException.class, () -> sql("UPDATE quiz_user_progress SET correct_answers=2 WHERE id=" + progressId));
        assertThrows(RuntimeException.class, () -> sql("INSERT INTO quiz_user_progress(user_subject,question_id) VALUES ('" + subject + "'," + id + ")"));
    }

    @Test
    void publicationChecksUnicodeCodePointsInMysqlToo() throws Throwable {
        long id = draft(null, 4);
        tx(() -> questions.findById(id).invoke(q -> {
            q.sentenceReading = "\uD83D\uDE00" + READING;
            q.targetStart = 1;
        }));
        run(() -> publication.publish(id));
        assertTrue(eligible(null, null).contains(id));
    }

    @Test
    void failedPublicationRollsBackAnEntireCreateTransaction() {
        long before = number("SELECT COUNT(*) FROM quiz_questions");
        assertThrows(ValidationException.class, () -> tx(() -> {
            QuizQuestion q = new QuizQuestion();
            q.sourceType = QuestionSource.CUSTOM;
            q.status = QuestionStatus.DRAFT;
            q.sentenceReading = READING;
            q.targetReading = READING;
            q.targetLength = 4;
            return questions.persist(q).chain(() -> publication.publish(q.id));
        }));
        assertEquals(before, number("SELECT COUNT(*) FROM quiz_questions"));
    }

    @Test
    void databaseRejectsInvalidSourcesDuplicateOptionsAndMultipleCorrectAnswers() throws Throwable {
        long id = draft(null, 4);
        assertThrows(RuntimeException.class, () -> sql("INSERT INTO quiz_question_options(question_id,option_text,is_correct) VALUES (" + id + ",'choice-0',0)"));
        assertThrows(RuntimeException.class, () -> sql("INSERT INTO quiz_question_options(question_id,option_text,is_correct) VALUES (" + id + ",'different',1)"));
        assertThrows(RuntimeException.class, () -> sql("UPDATE quiz_questions SET source_type='EXAMPLE' WHERE id=" + id));
        assertThrows(RuntimeException.class, () -> sql("UPDATE quiz_questions SET target_start=-1 WHERE id=" + id));
        run(() -> publication.publish(id));
        sql("UPDATE quiz_questions SET status='ARCHIVED' WHERE id=" + id);
        assertFalse(eligible(null, null).contains(id));
    }

    @Test
    void concurrentPublicationSerializesOnTheQuestionLock() throws Throwable {
        long id = draft(null, 4);
        Supplier<Boolean> publish = () -> {
            try {
                run(() -> publication.publish(id));
                return true;
            } catch (ValidationException alreadyPublished) {
                return false;
            } catch (Throwable failure) {
                throw new CompletionException(failure);
            }
        };
        var first = CompletableFuture.supplyAsync(publish);
        var second = CompletableFuture.supplyAsync(publish);
        assertNotEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        assertEquals(1, number("SELECT version FROM quiz_questions WHERE id=" + id));
        assertTrue(eligible(null, null).contains(id));
    }

    @Test
    void existingVocabularyEditsInvalidateNewSessionsWithoutChangingHistory() throws Throwable {
        long vocabularyId = tx(() -> {
            Vocabulary vocabulary = new Vocabulary();
            vocabulary.word = "quiz-" + UUID.randomUUID();
            vocabulary.normalizedWord = vocabulary.word;
            return Panache.getSession().chain(session -> session.persist(vocabulary)).replaceWith(() -> vocabulary.id);
        });
        var request = new VocabularyExampleUpdateRequest("source", READING, "test", "test", "source", 0);
        long exampleId = run(() -> exampleEdits.addVocabularyExamples(vocabularyId, List.of(request))).get(0).exampleId();
        long id = draft(exampleId, 4);
        run(() -> publication.publish(id));
        var saved = saveSnapshot(id, "source-update-user");
        run(() -> exampleEdits.updateVocabularyExample(vocabularyId, exampleId,
                new VocabularyExampleUpdateRequest("source", READING + ".", "test", "test", "source", 0)));
        assertFalse(eligible(null, null).contains(id));
        long before = number("SELECT COUNT(*) FROM quiz_sessions");
        assertThrows(ValidationException.class, () -> saveSnapshot(id, "source-update-user"));
        assertEquals(before, number("SELECT COUNT(*) FROM quiz_sessions"));
        assertEquals(READING, tx(() -> snapshots.findById(saved.id)).sentenceReading);
    }

    @Test
    void createsCompleteSessionsInRequestedOrderAndRejectsPartialOrDuplicateSelections() throws Throwable {
        long first = draft(null, 4);
        long second = draft(null, 4);
        run(() -> publication.publish(first));
        long before = number("SELECT COUNT(*) FROM quiz_sessions");
        assertThrows(ValidationException.class, () -> run(() -> sessionCreation.create("session-user", List.of(first, second))));
        assertThrows(ValidationException.class, () -> run(() -> sessionCreation.create("session-user", List.of(first, first))));
        assertEquals(before, number("SELECT COUNT(*) FROM quiz_sessions"));
        run(() -> publication.publish(second));
        var session = run(() -> sessionCreation.create("session-user", List.of(second, first)));
        var saved = tx(() -> snapshots.findBySessionId(session.id));
        assertEquals(2, session.questionCount);
        assertEquals(List.of(second, first), saved.stream().map(q -> q.questionId).toList());
        for (var question : saved) {
            assertEquals(4, tx(() -> snapshots.findOptions(question.id)).size());
        }
    }

    private long draft(Long exampleId, int optionCount) throws Throwable {
        return tx(() -> {
            QuizQuestion q = new QuizQuestion();
            q.sourceType = exampleId == null ? QuestionSource.CUSTOM : QuestionSource.EXAMPLE;
            q.exampleSentenceId = exampleId;
            q.sentenceReading = exampleId == null ? READING : null;
            q.targetReading = READING;
            q.targetLength = 4;
            q.status = QuestionStatus.DRAFT;
            return questions.persist(q).chain(() -> Multi.createFrom().range(0, optionCount)
                    .onItem().transformToUniAndConcatenate(i -> addOption(q.id, "choice-" + i, i == 0))
                    .collect().asList()).replaceWith(() -> q.id);
        });
    }

    private Uni<QuizQuestionOption> addOption(long questionId, String text, boolean correct) {
        QuizQuestionOption option = new QuizQuestionOption();
        option.questionId = questionId;
        option.optionText = text;
        option.correct = correct;
        return options.persist(option);
    }

    private QuizSessionQuestion saveSnapshot(long questionId, String subject) throws Throwable {
        QuizSession session = run(() -> sessionCreation.create(subject, List.of(questionId)));
        return tx(() -> snapshots.findBySessionId(session.id)).get(0);
    }

    private List<Long> eligible(Long levelId, Long lessonId) throws Throwable {
        return tx(() -> questions.findEligibleIds(levelId, lessonId, 0, 100));
    }

    private <T> T tx(Supplier<Uni<T>> work) throws Throwable {
        return run(() -> Panache.withTransaction(work));
    }

    private <T> T run(Supplier<Uni<T>> work) throws Throwable {
        return VertxContextSupport.subscribeAndAwait(work);
    }

    private void sql(String query) {
        pool.query(query).execute().await().atMost(Duration.ofSeconds(10));
    }

    private long number(String query) {
        return pool.query(query).execute().await().atMost(Duration.ofSeconds(10)).iterator().next().getLong(0);
    }
}
