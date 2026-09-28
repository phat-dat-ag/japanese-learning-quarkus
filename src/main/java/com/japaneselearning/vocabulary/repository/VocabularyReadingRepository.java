package com.japaneselearning.vocabulary.repository;

import com.japaneselearning.vocabulary.entity.VocabularyReading;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VocabularyReadingRepository implements PanacheRepository<VocabularyReading> {

    public Uni<VocabularyReading> findByVocabularyIdAndReading(
            Long vocabularyId,
            String reading) {

        return find(
                "vocabularyId = ?1 and reading = ?2",
                vocabularyId,
                reading
        ).firstResult();
    }

    public Uni<VocabularyReading> findReadingForVocabulary(Long vocabularyId, Long readingId) {
        return find("vocabularyId = ?1 and id = ?2", vocabularyId, readingId).firstResult();
    }

    public Uni<Long> countPrimaryReadingsExcluding(Long vocabularyId, Long excludedReadingId) {
        return count("vocabularyId = ?1 and isPrimary = true and id <> ?2", vocabularyId, excludedReadingId);
    }
}
