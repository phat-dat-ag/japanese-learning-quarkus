package com.japaneselearning.flashcard.repository;

import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.entity.VocabularyReading;
import com.japaneselearning.vocabulary.entity.VocabularyMeaning;
import com.japaneselearning.vocabulary.entity.PartOfSpeech;
import com.japaneselearning.vocabulary.entity.JlptLevel;
import com.japaneselearning.vocabulary.entity.Lesson;
import com.japaneselearning.vocabulary.entity.Kanji;
import com.japaneselearning.vocabulary.entity.KanjiReading;
import com.japaneselearning.vocabulary.entity.VocabularyExample;
import com.japaneselearning.vocabulary.entity.VocabularyPitchAccent;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class FlashcardRepository {

    public Uni<List<Vocabulary>> findVocabulary(
            String levelCode,
            Integer lessonNumber,
            int offset,
            int limit
    ) {
        String hql = lessonNumber == null
                ? """
                SELECT v
                FROM Vocabulary v
                JOIN VocabularyLevel vl ON vl.vocabularyId = v.id
                JOIN JlptLevel l ON l.id = vl.levelId
                WHERE l.code = :levelCode
                ORDER BY vl.displayOrder ASC, v.id ASC
                """
                : """
                SELECT v
                FROM Vocabulary v
                JOIN LessonVocabulary lv ON lv.vocabularyId = v.id
                JOIN Lesson l ON l.id = lv.lessonId
                JOIN JlptLevel jl ON jl.id = l.levelId
                WHERE jl.code = :levelCode
                AND l.lessonNumber = :lessonNumber
                ORDER BY lv.displayOrder ASC, v.id ASC
                """;

        return Panache.getSession()
                .flatMap(session -> {
                    var query = session.createQuery(hql, Vocabulary.class)
                            .setParameter("levelCode", levelCode)
                            .setFirstResult(offset)
                            .setMaxResults(limit);

                    if (lessonNumber != null) {
                        query.setParameter("lessonNumber", lessonNumber);
                    }

                    return query.getResultList();
                });
    }

    public Uni<Long> countVocabulary(String levelCode, Integer lessonNumber) {
        String hql = lessonNumber == null
                ? """
                SELECT COUNT(v)
                FROM Vocabulary v
                JOIN VocabularyLevel vl ON vl.vocabularyId = v.id
                JOIN JlptLevel l ON l.id = vl.levelId
                WHERE l.code = :levelCode
                """
                : """
                SELECT COUNT(lv)
                FROM LessonVocabulary lv
                JOIN Lesson l ON l.id = lv.lessonId
                JOIN JlptLevel jl ON jl.id = l.levelId
                WHERE jl.code = :levelCode
                AND l.lessonNumber = :lessonNumber
                """;

        return Panache.getSession()
                .flatMap(session -> {
                    var query = session.createQuery(hql, Long.class)
                            .setParameter("levelCode", levelCode);

                    if (lessonNumber != null) {
                        query.setParameter("lessonNumber", lessonNumber);
                    }

                    return query.getSingleResult();
                });
    }

    public Uni<Vocabulary> findVocabularyById(Long vocabularyId) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createQuery("""
                                        SELECT v
                                        FROM Vocabulary v
                                        WHERE v.id = :vocabularyId
                                        """, Vocabulary.class)
                                .setParameter("vocabularyId", vocabularyId)
                                .getSingleResult()
                );
    }

    public Uni<List<VocabularyReading>> findReadings(Long vocabularyId) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createQuery("""
                                        SELECT vr
                                        FROM VocabularyReading vr
                                        WHERE vr.vocabularyId = :vocabularyId
                                        ORDER BY vr.displayOrder ASC, vr.id ASC
                                        """, VocabularyReading.class)
                                .setParameter("vocabularyId", vocabularyId)
                                .getResultList()
                );
    }

    public Uni<List<VocabularyMeaning>> findMeanings(Long vocabularyId) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createQuery("""
                                        SELECT vm
                                        FROM VocabularyMeaning vm
                                        WHERE vm.vocabularyId = :vocabularyId
                                        ORDER BY vm.languageCode ASC, vm.displayOrder ASC, vm.id ASC
                                        """, VocabularyMeaning.class)
                                .setParameter("vocabularyId", vocabularyId)
                                .getResultList()
                );
    }

    public Uni<List<PartOfSpeech>> findPartsOfSpeech(Long vocabularyId) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createQuery("""
                                        SELECT pos
                                        FROM VocabularyPartOfSpeech vpos
                                        JOIN PartOfSpeech pos ON pos.id = vpos.partOfSpeechId
                                        WHERE vpos.vocabularyId = :vocabularyId
                                        ORDER BY pos.id ASC
                                        """, PartOfSpeech.class)
                                .setParameter("vocabularyId", vocabularyId)
                                .getResultList()
                );
    }

    public Uni<List<JlptLevel>> findLevels(Long vocabularyId) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createQuery("""
                                        SELECT l
                                        FROM VocabularyLevel vl
                                        JOIN JlptLevel l ON l.id = vl.levelId
                                        WHERE vl.vocabularyId = :vocabularyId
                                        ORDER BY vl.displayOrder ASC, l.displayOrder ASC
                                        """, JlptLevel.class)
                                .setParameter("vocabularyId", vocabularyId)
                                .getResultList()
                );
    }

    public Uni<List<Lesson>> findLessons(Long vocabularyId) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createQuery("""
                                        SELECT l
                                        FROM LessonVocabulary lv
                                        JOIN Lesson l ON l.id = lv.lessonId
                                        JOIN FETCH l.level jl
                                        WHERE lv.vocabularyId = :vocabularyId
                                        ORDER BY jl.displayOrder ASC, l.displayOrder ASC
                                        """, Lesson.class)
                                .setParameter("vocabularyId", vocabularyId)
                                .getResultList()
                );
    }

    public Uni<List<Kanji>> findKanji(Long vocabularyId) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createQuery("""
                                        SELECT k
                                        FROM VocabularyKanji vk
                                        JOIN Kanji k ON k.id = vk.kanjiId
                                        WHERE vk.vocabularyId = :vocabularyId
                                        ORDER BY vk.displayOrder ASC, k.id ASC
                                        """, Kanji.class)
                                .setParameter("vocabularyId", vocabularyId)
                                .getResultList()
                );
    }

    public Uni<List<KanjiReading>> findKanjiReadings(List<Long> kanjiIds) {

        if (kanjiIds.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }

        return Panache.getSession()
                .flatMap(session ->
                        session.createQuery("""
                                        SELECT kr
                                        FROM KanjiReading kr
                                        WHERE kr.kanjiId IN (:kanjiIds)
                                        ORDER BY kr.kanjiId ASC, kr.displayOrder ASC, kr.id ASC
                                        """, KanjiReading.class)
                                .setParameter("kanjiIds", kanjiIds)
                                .getResultList()
                );
    }

    public Uni<List<VocabularyExample>> findExamples(Long vocabularyId) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createQuery("""
                                        SELECT ve
                                        FROM VocabularyExample ve
                                        JOIN FETCH ve.exampleSentence es
                                        WHERE ve.vocabularyId = :vocabularyId
                                        ORDER BY ve.displayOrder ASC, es.id ASC
                                        """, VocabularyExample.class)
                                .setParameter("vocabularyId", vocabularyId)
                                .getResultList()
                );
    }

    public Uni<List<VocabularyPitchAccent>> findPitchAccentsByReadingIds(List<Long> readingIds) {

        if (readingIds.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }

        return Panache.getSession()
                .flatMap(session ->
                        session.createQuery("""
                                        SELECT pa
                                        FROM VocabularyPitchAccent pa
                                        WHERE pa.vocabularyReadingId IN (:readingIds)
                                        ORDER BY pa.vocabularyReadingId, pa.accentPattern
                                        """, VocabularyPitchAccent.class)
                                .setParameter("readingIds", readingIds)
                                .getResultList()
                );
    }
}