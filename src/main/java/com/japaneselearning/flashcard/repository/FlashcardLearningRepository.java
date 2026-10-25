package com.japaneselearning.flashcard.repository;

import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.flashcard.domain.FlashcardSessionMode;
import com.japaneselearning.flashcard.service.FlashcardValidation;
import com.japaneselearning.vocabulary.entity.JlptLevel;
import com.japaneselearning.vocabulary.entity.Lesson;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class FlashcardLearningRepository {
    public enum Collection {CATALOGUE, DUE, BOOKMARKS, FORGOTTEN}

    public Uni<Void> validateScope(String level, Long lessonId) {
        if (lessonId != null && lessonId <= 0) {
            throw FlashcardValidation.invalid("lessonId", "Lesson ID must be positive");
        }

        if (lessonId == null) {
            return Uni.createFrom().voidItem();
        }

        return Panache.getSession().chain(session -> session.find(Lesson.class, lessonId)
                .onItem().ifNull().failWith(
                        () -> new ResourceNotFoundException("LESSON_NOT_FOUND", "Lesson not found")
                )
                .chain(lesson -> {
                    if (level == null) {
                        return Uni.createFrom().voidItem();
                    }

                    return session.find(JlptLevel.class, lesson.levelId).invoke(jlpt -> {
                        if (jlpt == null || !level.equals(jlpt.code)) {
                            throw FlashcardValidation.invalid(
                                    "lessonId",
                                    "Lesson must belong to the selected level"
                            );
                        }
                    }).replaceWithVoid();
                })
        );
    }

    public Uni<List<Long>> select(
            String subject,
            Long lessonId,
            FlashcardSessionMode mode,
            LocalDateTime now,
            int limit,
            boolean shuffle
    ) {
        if (mode == FlashcardSessionMode.ALL) {
            return selectPractice(subject, lessonId, limit, shuffle);
        }

        String condition = switch (mode) {
            case ALL -> "";
            case DUE -> " and p.nextReviewAt <= :now";
            case NEW_AND_DUE -> " and (p.id is null or p.nextReviewAt <= :now)";
        };

        String hql = """
                select lv.vocabularyId from LessonVocabulary lv
                left join VocabularyProgress p on p.vocabularyId = lv.vocabularyId and p.userSubject = :subject
                where lv.lessonId = :lesson
                """ + condition + " order by case when p.id is null then 1 else 0 end, p.nextReviewAt, lv.displayOrder, lv.vocabularyId";

        return Panache.getSession().chain(session -> {
            var query = session.createQuery(hql, Long.class)
                    .setParameter("subject", subject)
                    .setParameter("lesson", lessonId).setMaxResults(limit);

            if (mode.scheduling()) {
                query.setParameter("now", now);
            }

            return query.getResultList();
        });
    }

    private Uni<List<Long>> selectPractice(String subject, Long lessonId, int limit, boolean shuffle) {
        // Sample before LIMIT in MySQL; transfer only the bounded selected IDs.
        String order = shuffle ? "rand(), lv.vocabulary_id" : """
                (select max(r.reviewed_at) from flashcard_review_history r
                 where r.user_subject = :subject and r.vocabulary_id = lv.vocabulary_id
                   and r.scheduling = false), lv.display_order, lv.vocabulary_id
                """;

        return Panache.getSession().chain(session -> {
            var query = session.createNativeQuery("select lv.vocabulary_id from lesson_vocabulary lv "
                            + "where lv.lesson_id = :lesson order by " + order, Long.class)
                    .setParameter("lesson", lessonId)
                    .setMaxResults(limit);

            if (!shuffle) query.setParameter("subject", subject);

            return query.getResultList();
        });
    }

    public Uni<List<Object[]>> cards(
            String subject,
            String level,
            Long lesson,
            Collection collection,
            LocalDateTime now,
            int page,
            int size
    ) {
        String hql = "select v.id, v.word "
                + cardFrom(collection)
                + scope(false, level, lesson)
                + switch (collection) {
            case DUE -> " order by p.nextReviewAt, v.id";
            case BOOKMARKS -> " order by b.createdAt desc, v.id desc";
            case FORGOTTEN -> " group by v.id, v.word order by count(r.id) desc, v.id";
            case CATALOGUE -> " order by v.id";
        };

        return Panache.getSession().chain(session -> {
            var query = session.createQuery(hql, Object[].class)
                    .setFirstResult(page * size)
                    .setMaxResults(size);

            if (collection != Collection.CATALOGUE)
                query.setParameter("subject", subject);

            if (collection == Collection.DUE)
                query.setParameter("now", now);

            if (level != null)
                query.setParameter("level", level);

            if (lesson != null)
                query.setParameter("lesson", lesson);

            return query.getResultList();
        });
    }

    public Uni<Long> countCards(
            String subject,
            String level,
            Long lesson,
            Collection collection,
            LocalDateTime now
    ) {
        String hql = "select count(distinct v.id) "
                + cardFrom(collection)
                + scope(false, level, lesson);

        return Panache.getSession().chain(session -> {
            var query = session.createQuery(hql, Long.class);

            if (collection != Collection.CATALOGUE)
                query.setParameter("subject", subject);

            if (collection == Collection.DUE)
                query.setParameter("now", now);

            if (level != null)
                query.setParameter("level", level);

            if (lesson != null)
                query.setParameter("lesson", lesson);

            return query.getSingleResult();
        });
    }

    public Uni<Object[]> progressCounts(
            String subject,
            String level,
            Long lesson,
            LocalDateTime now
    ) {
        String sql = """
                select count(*), coalesce(sum(p.id is null), 0),
                coalesce(sum(p.status = 'LEARNING'), 0), coalesce(sum(p.status = 'REVIEW'), 0),
                coalesce(sum(p.status = 'MASTERED'), 0), coalesce(sum(p.next_review_at <= :now), 0)
                from vocabulary v left join user_vocabulary_progress p
                on p.vocabulary_id = v.id and p.user_subject = :subject where 1=1
                """ + scope(true, level, lesson);

        return Panache.getSession().chain(session -> {
            var query = session.createNativeQuery(sql, Object[].class)
                    .setParameter("subject", subject)
                    .setParameter("now", now);

            if (level != null)
                query.setParameter("level", level);

            if (lesson != null)
                query.setParameter("lesson", lesson);

            return query.getSingleResult();
        });
    }

    public Uni<Object[]> reviewCounts(String subject, String level, Long lesson) {
        String sql = """
                select count(distinct r.vocabulary_id), count(*), coalesce(sum(r.rating = 'AGAIN'), 0),
                coalesce(sum(r.scheduling = true), 0)
                from flashcard_review_history r join vocabulary v on v.id = r.vocabulary_id
                where r.user_subject = :subject
                """ + scope(true, level, lesson);

        return Panache.getSession().chain(session -> {
            var query = session.createNativeQuery(sql, Object[].class).setParameter("subject", subject);

            if (level != null)
                query.setParameter("level", level);

            if (lesson != null)
                query.setParameter("lesson", lesson);

            return query.getSingleResult();
        });
    }

    private String cardFrom(Collection collection) {
        return switch (collection) {
            case CATALOGUE -> "from Vocabulary v where 1=1";
            case DUE -> "from Vocabulary v join VocabularyProgress p on p.vocabularyId = v.id "
                    + "where p.userSubject = :subject and p.nextReviewAt <= :now";
            case BOOKMARKS -> "from Vocabulary v join VocabularyBookmark b on b.vocabularyId = v.id "
                    + "where b.userSubject = :subject";
            case FORGOTTEN -> "from Vocabulary v join FlashcardReview r on r.vocabularyId = v.id "
                    + "where r.userSubject = :subject and r.rating = 'AGAIN'";
        };
    }

    private String scope(boolean sql, String level, Long lesson) {
        String result = "";

        if (level != null) {
            result += sql
                    ? " and exists (select 1 from vocabulary_levels vl join jlpt_levels jl on jl.id = vl.level_id where vl.vocabulary_id = v.id and jl.code = :level)"
                    : " and exists (select 1 from VocabularyLevel vl join JlptLevel jl on jl.id = vl.levelId where vl.vocabularyId = v.id and jl.code = :level)";
        }

        if (lesson != null) {
            result += sql
                    ? " and exists (select 1 from lesson_vocabulary lv where lv.vocabulary_id = v.id and lv.lesson_id = :lesson)"
                    : " and exists (select 1 from LessonVocabulary lv where lv.vocabularyId = v.id and lv.lessonId = :lesson)";
        }

        return result;
    }
}
