package com.japaneselearning.quiz.admin.repository;

import com.japaneselearning.quiz.admin.dto.QuizQuestionFilter;
import com.japaneselearning.quiz.domain.QuestionSource;
import com.japaneselearning.quiz.entity.QuizQuestion;
import com.japaneselearning.quiz.entity.QuizQuestionLesson;
import com.japaneselearning.quiz.entity.QuizQuestionLevel;
import com.japaneselearning.quiz.entity.QuizQuestionOption;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class QuizAdminRepository implements PanacheRepository<QuizQuestion> {
    public Uni<List<QuizQuestion>> search(QuizQuestionFilter filter) {
        Map<String, Object> parameters = new HashMap<>();
        String predicate = predicate(filter, parameters);

        return find(predicate + " order by id desc", parameters)
                .range(filter.page() * filter.size(), filter.page() * filter.size() + filter.size() - 1)
                .list();
    }

    public Uni<Long> countMatching(QuizQuestionFilter filter) {
        Map<String, Object> parameters = new HashMap<>();

        return count(predicate(filter, parameters), parameters);
    }

    private String predicate(QuizQuestionFilter filter, Map<String, Object> parameters) {
        StringBuilder query = new StringBuilder("1 = 1");

        add(query, parameters, "sourceType", filter.sourceType());
        add(query, parameters, "status", filter.status());
        add(query, parameters, "vocabularyId", filter.vocabularyId());
        add(query, parameters, "exampleSentenceId", filter.exampleId());

        if (filter.levelId() != null) {
            query.append("""
                     and (id in (select questionId from QuizQuestionLevel where levelId = :levelId)
                     or id in (select ql.questionId from QuizQuestionLesson ql, Lesson l
                               where ql.lessonId = l.id and l.levelId = :levelId))
                    """);

            parameters.put("levelId", filter.levelId());
        }

        if (filter.lessonId() != null) {
            query.append(" and id in (select questionId from QuizQuestionLesson where lessonId = :lessonId)");
            parameters.put("lessonId", filter.lessonId());

            if (filter.levelId() != null) {
                query.append(" and :lessonId in (select id from Lesson where levelId = :levelId)");
            }
        }
        if (filter.keyword() != null && !filter.keyword().isBlank()) {
            query.append("""
                     and (locate(:keyword, sentenceReading) > 0 or locate(:keyword, targetReading) > 0
                     or locate(:keyword, explanationVi) > 0 or locate(:keyword, explanationEn) > 0
                     or exampleSentenceId in (select id from ExampleSentence
                                             where locate(:keyword, japaneseReading) > 0))
                    """);

            parameters.put("keyword", filter.keyword().trim());
        }

        return query.toString();
    }

    private void add(StringBuilder query, Map<String, Object> parameters, String field, Object value) {
        if (value != null) {
            query.append(" and ").append(field).append(" = :").append(field);
            parameters.put(field, value);
        }
    }

    public Uni<Boolean> hasDuplicate(QuizQuestion question) {
        String identity = question.sourceType == QuestionSource.EXAMPLE
                ? "exampleSentenceId = :source" : "sentenceReading = :source";
        Object source = question.sourceType == QuestionSource.EXAMPLE
                ? question.exampleSentenceId : question.sentenceReading;

        return count("sourceType = :type and "
                        + identity
                        + " and targetStart = :start and targetLength = :length and id <> :id",
                Map.of("type", question.sourceType,
                        "source", source,
                        "start", question.targetStart,
                        "length", question.targetLength,
                        "id", question.id == null ? 0L : question.id)
        ).map(count -> count > 0);
    }

    public Uni<Boolean> referenceExists(String entity, Long id) {
        // Entity names are internal constants supplied by the service, never client input.
        return Panache.getSession().chain(session -> session.createQuery(
                        "select count(*) from " + entity + " where id = :id", Long.class
                )
                .setParameter("id", id).getSingleResult()).map(count -> count > 0);
    }

    public Uni<Void> replaceClassifications(Long questionId, List<Long> levelIds, List<Long> lessonIds) {
        return Panache.getSession().chain(session -> session.createMutationQuery(
                        "delete from QuizQuestionLevel where questionId = :id"
                )
                .setParameter("id", questionId).executeUpdate()
                .chain(() -> session.createMutationQuery("delete from QuizQuestionLesson where questionId = :id")
                        .setParameter("id", questionId).executeUpdate())
                .chain(() -> Multi.createFrom().iterable(levelIds)
                        .onItem().transformToUniAndConcatenate(id -> {
                            QuizQuestionLevel level = new QuizQuestionLevel();

                            level.questionId = questionId;
                            level.levelId = id;

                            return session.persist(level);
                        }).collect().asList())
                .chain(() -> Multi.createFrom().iterable(lessonIds)
                        .onItem().transformToUniAndConcatenate(id -> {
                            QuizQuestionLesson lesson = new QuizQuestionLesson();

                            lesson.questionId = questionId;
                            lesson.lessonId = id;

                            return session.persist(lesson);
                        }).collect().asList())).replaceWithVoid();
    }

    public Uni<List<QuizQuestionOption>> options(List<Long> ids) {
        return Panache.getSession().chain(session -> session.createQuery(
                        "from QuizQuestionOption where questionId in :ids order by id",
                        QuizQuestionOption.class
                )
                .setParameter("ids", ids).getResultList());
    }

    public Uni<List<QuizQuestionLevel>> levels(List<Long> ids) {
        return Panache.getSession().chain(session -> session.createQuery(
                        "from QuizQuestionLevel where questionId in :ids order by levelId",
                        QuizQuestionLevel.class
                )
                .setParameter("ids", ids).getResultList());
    }

    public Uni<List<QuizQuestionLesson>> lessons(List<Long> ids) {
        return Panache.getSession().chain(session -> session.createQuery(
                        "from QuizQuestionLesson where questionId in :ids order by lessonId",
                        QuizQuestionLesson.class
                )
                .setParameter("ids", ids).getResultList());
    }

    public Uni<List<Object[]>> sources(List<Long> ids) {
        return Panache.getSession().chain(session -> session.createNativeQuery("""
                SELECT q.id, e.japanese_reading,
                       SHA2(CONCAT(e.japanese_reading, CHAR(0), CAST(e.updated_at AS CHAR)), 256)
                FROM quiz_questions q JOIN example_sentences e ON e.id = q.example_sentence_id
                WHERE q.id IN (:ids)
                """, Object[].class
        ).setParameter("ids", ids).getResultList());
    }
}
