package com.japaneselearning.vocabulary.repository;

import com.japaneselearning.vocabulary.entity.VocabularyMeaning;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VocabularyMeaningRepository implements PanacheRepository<VocabularyMeaning> {
    public Uni<VocabularyMeaning> findMeaningForVocabulary(Long vocabularyId, Long meaningId) {
        return find("vocabularyId = ?1 and id = ?2", vocabularyId, meaningId).firstResult();
    }

    public Uni<VocabularyMeaning> findByVocabularyAndLanguageAndMeaning(
            Long vocabularyId,
            String languageCode,
            String meaning
    ) {
        return find(
                "vocabularyId = ?1 and languageCode = ?2 and meaning = ?3",
                vocabularyId, languageCode, meaning).firstResult();
    }
}
