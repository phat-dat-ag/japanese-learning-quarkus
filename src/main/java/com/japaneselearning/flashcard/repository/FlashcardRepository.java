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

    public Uni<List<Object[]>> findVocabularyByLevel(
            String levelCode,
            int offset,
            int limit
    ) {
        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeQuery("""
                                        SELECT
                                            v.id,
                                            v.word
                                        FROM vocabulary v
                                        INNER JOIN vocabulary_levels vl
                                            ON vl.vocabulary_id = v.id
                                        INNER JOIN jlpt_levels l
                                            ON l.id = vl.level_id
                                        WHERE l.code = :levelCode
                                        ORDER BY
                                            vl.display_order ASC,
                                            v.id ASC
                                        LIMIT :limit
                                        OFFSET :offset
                                        """)
                                .setParameter("levelCode", levelCode)
                                .setParameter("limit", limit)
                                .setParameter("offset", offset)
                                .getResultList()
                )
                .map(rows ->
                        rows.stream()
                                .map(row -> (Object[]) row)
                                .toList()
                );
    }

    public Uni<Long> countVocabularyByLevel(
            String levelCode
    ) {
        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeQuery("""
                                        SELECT COUNT(*)
                                        FROM vocabulary v
                                        INNER JOIN vocabulary_levels vl
                                            ON vl.vocabulary_id = v.id
                                        INNER JOIN jlpt_levels l
                                            ON l.id = vl.level_id
                                        WHERE l.code = :levelCode
                                        """)
                                .setParameter("levelCode", levelCode)
                                .getSingleResult()
                )
                .map(result -> ((Number) result).longValue());
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

    /**
     * Lấy pitch accent theo reading.
     */
    public Uni<List<Object>> findPitchAccents(
            Long vocabularyReadingId
    ) {
        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeQuery("""
                                        SELECT accent_pattern
                                        FROM vocabulary_pitch_accents
                                        WHERE vocabulary_reading_id = :readingId
                                        ORDER BY accent_pattern ASC
                                        """)
                                .setParameter(
                                        "readingId",
                                        vocabularyReadingId
                                )
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

    public Uni<List<Object[]>> findVocabularyByLevelAndLesson(
            String levelCode,
            Integer lessonNumber,
            int offset,
            int size
    ) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeQuery("""
                                                SELECT
                                                    v.id,
                                                    v.word
                                                FROM vocabulary v
                                                INNER JOIN lesson_vocabulary lv
                                                    ON lv.vocabulary_id = v.id
                                                INNER JOIN lessons l
                                                    ON l.id = lv.lesson_id
                                                INNER JOIN jlpt_levels jl
                                                    ON jl.id = l.level_id
                                                WHERE jl.code = :levelCode
                                                  AND l.lesson_number = :lessonNumber
                                                ORDER BY
                                                    lv.display_order ASC,
                                                    v.id ASC
                                                LIMIT :size OFFSET :offset
                                                """,
                                        Object[].class
                                )
                                .setParameter(
                                        "levelCode",
                                        levelCode
                                )
                                .setParameter(
                                        "lessonNumber",
                                        lessonNumber
                                )
                                .setParameter(
                                        "size",
                                        size
                                )
                                .setParameter(
                                        "offset",
                                        offset
                                )
                                .getResultList()
                );
    }

    public Uni<Long> countVocabularyByLevelAndLesson(
            String levelCode,
            Integer lessonNumber
    ) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeQuery("""
                                                SELECT COUNT(*)
                                                FROM lesson_vocabulary lv
                                                INNER JOIN lessons l
                                                    ON l.id = lv.lesson_id
                                                INNER JOIN jlpt_levels jl
                                                    ON jl.id = l.level_id
                                                WHERE jl.code = :levelCode
                                                  AND l.lesson_number = :lessonNumber
                                                """,
                                        Long.class
                                )
                                .setParameter(
                                        "levelCode",
                                        levelCode
                                )
                                .setParameter(
                                        "lessonNumber",
                                        lessonNumber
                                )
                                .getSingleResult()
                );
    }
}