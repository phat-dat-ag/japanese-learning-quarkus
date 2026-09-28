package com.japaneselearning.vocabulary.repository;

import com.japaneselearning.vocabulary.entity.VocabularyPitchAccent;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VocabularyPitchAccentRepository implements PanacheRepository<VocabularyPitchAccent> {
    public Uni<VocabularyPitchAccent> findPitchAccentForVocabulary(Long vocabularyId, Long pitchAccentId) {
        return find(
                "id = ?1 and vocabularyReadingId in ("
                        + "select reading.id from VocabularyReading reading where reading.vocabularyId = ?2)",
                pitchAccentId, vocabularyId).firstResult();
    }

    public Uni<VocabularyPitchAccent> findByReadingAndAccentPattern(Long readingId, Integer accentPattern) {
        return find("vocabularyReadingId = ?1 and accentPattern = ?2", readingId, accentPattern).firstResult();
    }
}
