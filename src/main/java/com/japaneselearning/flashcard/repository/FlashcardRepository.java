package com.japaneselearning.flashcard.repository;

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

    public Uni<List<Object[]>> findReadings(
            List<Long> vocabularyIds
    ) {
        if (vocabularyIds.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeQuery("""
                                        SELECT
                                            id,
                                            vocabulary_id,
                                            reading,
                                            is_primary
                                        FROM vocabulary_readings
                                        WHERE vocabulary_id IN (:ids)
                                        ORDER BY
                                            vocabulary_id,
                                            display_order
                                        """)
                                .setParameter("ids", vocabularyIds)
                                .getResultList()
                )
                .map(rows ->
                        rows.stream()
                                .map(row -> (Object[]) row)
                                .toList()
                );
    }

    public Uni<List<Object[]>> findMeanings(
            List<Long> vocabularyIds
    ) {
        if (vocabularyIds.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeQuery("""
                                        SELECT
                                            vocabulary_id,
                                            language_code,
                                            meaning,
                                            is_primary
                                        FROM vocabulary_meanings
                                        WHERE vocabulary_id IN (:ids)
                                        ORDER BY
                                            vocabulary_id,
                                            language_code,
                                            display_order
                                        """)
                                .setParameter("ids", vocabularyIds)
                                .getResultList()
                )
                .map(rows ->
                        rows.stream()
                                .map(row -> (Object[]) row)
                                .toList()
                );
    }

    public Uni<List<Object[]>> findPartsOfSpeech(
            List<Long> vocabularyIds
    ) {
        if (vocabularyIds.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeQuery("""
                                        SELECT
                                            vpos.vocabulary_id,
                                            pos.code,
                                            pos.name_vi,
                                            pos.name_en
                                        FROM vocabulary_parts_of_speech vpos
                                        INNER JOIN parts_of_speech pos
                                            ON pos.id = vpos.part_of_speech_id
                                        WHERE vpos.vocabulary_id IN (:ids)
                                        ORDER BY
                                            vpos.vocabulary_id
                                        """)
                                .setParameter("ids", vocabularyIds)
                                .getResultList()
                )
                .map(rows ->
                        rows.stream()
                                .map(row -> (Object[]) row)
                                .toList()
                );
    }

    /**
     * Lấy vocabulary cơ bản.
     */
    public Uni<Object[]> findVocabularyById(Long vocabularyId) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeQuery("""
                                        SELECT
                                            id,
                                            word
                                        FROM vocabulary
                                        WHERE id = :vocabularyId
                                        """)
                                .setParameter("vocabularyId", vocabularyId)
                                .getSingleResult()
                )
                .map(result -> (Object[]) result);
    }

    /**
     * Lấy readings của vocabulary.
     */
    public Uni<List<Object[]>> findReadings(Long vocabularyId) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeQuery("""
                                        SELECT
                                            vr.id,
                                            vr.reading,
                                            vr.is_primary
                                        FROM vocabulary_readings vr
                                        WHERE vr.vocabulary_id = :vocabularyId
                                        ORDER BY
                                            vr.display_order ASC,
                                            vr.id ASC
                                        """)
                                .setParameter("vocabularyId", vocabularyId)
                                .getResultList()
                )
                .map(rows ->
                        rows.stream()
                                .map(row -> (Object[]) row)
                                .toList()
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

    /**
     * Lấy meanings.
     */
    public Uni<List<Object[]>> findMeanings(Long vocabularyId) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeQuery("""
                                        SELECT
                                            language_code,
                                            meaning,
                                            is_primary
                                        FROM vocabulary_meanings
                                        WHERE vocabulary_id = :vocabularyId
                                        ORDER BY
                                            language_code ASC,
                                            display_order ASC,
                                            id ASC
                                        """)
                                .setParameter("vocabularyId", vocabularyId)
                                .getResultList()
                )
                .map(rows ->
                        rows.stream()
                                .map(row -> (Object[]) row)
                                .toList()
                );
    }

    /**
     * Lấy parts of speech.
     */
    public Uni<List<Object[]>> findPartsOfSpeech(
            Long vocabularyId
    ) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeQuery("""
                                        SELECT
                                            pos.code,
                                            pos.name_vi,
                                            pos.name_en
                                        FROM vocabulary_parts_of_speech vpos
                                        INNER JOIN parts_of_speech pos
                                            ON pos.id = vpos.part_of_speech_id
                                        WHERE vpos.vocabulary_id = :vocabularyId
                                        ORDER BY pos.id ASC
                                        """)
                                .setParameter(
                                        "vocabularyId",
                                        vocabularyId
                                )
                                .getResultList()
                )
                .map(rows ->
                        rows.stream()
                                .map(row -> (Object[]) row)
                                .toList()
                );
    }

    /**
     * Lấy JLPT levels.
     */
    public Uni<List<Object[]>> findLevels(Long vocabularyId) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeQuery("""
                                        SELECT
                                            l.code,
                                            l.name
                                        FROM vocabulary_levels vl
                                        INNER JOIN jlpt_levels l
                                            ON l.id = vl.level_id
                                        WHERE vl.vocabulary_id = :vocabularyId
                                        ORDER BY
                                            vl.display_order ASC,
                                            l.display_order ASC
                                        """)
                                .setParameter(
                                        "vocabularyId",
                                        vocabularyId
                                )
                                .getResultList()
                )
                .map(rows ->
                        rows.stream()
                                .map(row -> (Object[]) row)
                                .toList()
                );
    }

    /**
     * Lấy Kanji của vocabulary.
     */
    public Uni<List<Object[]>> findKanji(Long vocabularyId) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeQuery("""
                                        SELECT
                                            k.id,
                                            k.kanji_character,
                                            k.stroke_count,
                                            k.meaning_vi,
                                            k.meaning_en
                                        FROM vocabulary_kanji vk
                                        INNER JOIN kanji k
                                            ON k.id = vk.kanji_id
                                        WHERE vk.vocabulary_id = :vocabularyId
                                        ORDER BY
                                            vk.display_order ASC,
                                            k.id ASC
                                        """)
                                .setParameter(
                                        "vocabularyId",
                                        vocabularyId
                                )
                                .getResultList()
                )
                .map(rows ->
                        rows.stream()
                                .map(row -> (Object[]) row)
                                .toList()
                );
    }

    /**
     * Lấy readings của các Kanji.
     */
    public Uni<List<Object[]>> findKanjiReadings(
            List<Long> kanjiIds
    ) {

        if (kanjiIds.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeQuery("""
                                        SELECT
                                            kr.kanji_id,
                                            kr.reading,
                                            kr.reading_type
                                        FROM kanji_readings kr
                                        WHERE kr.kanji_id IN (:kanjiIds)
                                        ORDER BY
                                            kr.kanji_id ASC,
                                            kr.display_order ASC,
                                            kr.id ASC
                                        """)
                                .setParameter("kanjiIds", kanjiIds)
                                .getResultList()
                )
                .map(rows ->
                        rows.stream()
                                .map(row -> (Object[]) row)
                                .toList()
                );
    }

    /**
     * Lấy example sentences.
     */
    public Uni<List<Object[]>> findExamples(Long vocabularyId) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeQuery("""
                                        SELECT
                                            es.japanese_text,
                                            es.japanese_reading,
                                            es.meaning_vi,
                                            es.meaning_en,
                                            ve.target_text
                                        FROM vocabulary_examples ve
                                        INNER JOIN example_sentences es
                                            ON es.id = ve.example_sentence_id
                                        WHERE ve.vocabulary_id = :vocabularyId
                                        ORDER BY
                                            ve.display_order ASC,
                                            es.id ASC
                                        """)
                                .setParameter(
                                        "vocabularyId",
                                        vocabularyId
                                )
                                .getResultList()
                )
                .map(rows ->
                        rows.stream()
                                .map(row -> (Object[]) row)
                                .toList()
                );
    }

    public Uni<List<Object[]>> findPitchAccentsByReadingIds(
            List<Long> readingIds
    ) {
        if (readingIds.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeQuery("""
                                        SELECT
                                            vocabulary_reading_id,
                                            accent_pattern
                                        FROM vocabulary_pitch_accents
                                        WHERE vocabulary_reading_id IN (:readingIds)
                                        ORDER BY
                                            vocabulary_reading_id,
                                            accent_pattern
                                        """)
                                .setParameter("readingIds", readingIds)
                                .getResultList()
                )
                .map(rows ->
                        rows.stream()
                                .map(row -> (Object[]) row)
                                .toList()
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

    public Uni<List<Object[]>> findLessons(Long vocabularyId) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeQuery("""
                                        SELECT
                                            jl.code,
                                            jl.name,
                                            l.lesson_number,
                                            l.title,
                                            l.description,
                                            l.display_order
                                        FROM lesson_vocabulary lv
                                        INNER JOIN lessons l
                                            ON l.id = lv.lesson_id
                                        INNER JOIN jlpt_levels jl
                                            ON jl.id = l.level_id
                                        WHERE lv.vocabulary_id = :vocabularyId
                                        ORDER BY
                                            jl.display_order ASC,
                                            l.display_order ASC
                                        """)
                                .setParameter(
                                        "vocabularyId",
                                        vocabularyId
                                )
                                .getResultList()
                )
                .map(rows ->
                        rows.stream()
                                .map(row -> (Object[]) row)
                                .toList()
                );
    }
}