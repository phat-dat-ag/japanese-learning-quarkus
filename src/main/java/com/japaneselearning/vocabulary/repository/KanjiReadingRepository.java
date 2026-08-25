package com.japaneselearning.vocabulary.repository;

import com.japaneselearning.vocabulary.entity.KanjiReading;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class KanjiReadingRepository
        implements PanacheRepository<KanjiReading> {

    public Uni<KanjiReading> findByKanjiIdAndReadingAndType(
            Long kanjiId,
            String reading,
            String readingType) {

        return find(
                "kanjiId = ?1 and reading = ?2 and readingType = ?3",
                kanjiId,
                reading,
                readingType
        ).firstResult();
    }
}