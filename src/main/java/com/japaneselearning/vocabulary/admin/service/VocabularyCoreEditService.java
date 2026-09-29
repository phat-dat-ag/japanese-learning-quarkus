package com.japaneselearning.vocabulary.admin.service;

import com.japaneselearning.vocabulary.admin.dto.VocabularyCoreUpdateRequest;
import com.japaneselearning.vocabulary.admin.dto.VocabularyCoreResponse;
import com.japaneselearning.vocabulary.repository.VocabularyRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VocabularyCoreEditService {
    private final VocabularyEditPersistence persistence;
    private final VocabularyRepository vocabularies;

    public VocabularyCoreEditService(
            VocabularyEditPersistence persistence,
            VocabularyRepository vocabularies
    ) {
        this.persistence = persistence;
        this.vocabularies = vocabularies;
    }

    @WithTransaction
    public Uni<VocabularyCoreResponse> updateVocabularyCore(
            Long vocabularyId,
            VocabularyCoreUpdateRequest request
    ) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .call(() -> requireUniqueNormalizedWord(vocabularyId, request.normalizedWord()))
                .map(vocabulary -> {
                    vocabulary.word = request.word();
                    vocabulary.normalizedWord = request.normalizedWord();
                    return new VocabularyCoreResponse(vocabularyId);
                })
        );
    }

    private Uni<Void> requireUniqueNormalizedWord(Long vocabularyId, String normalizedWord) {
        return vocabularies.findByNormalizedWord(normalizedWord)
                .invoke(existingVocabulary -> {
                    if (existingVocabulary != null && !existingVocabulary.id.equals(vocabularyId)) {
                        throw VocabularyEditPersistence.conflict(
                                "Normalized word already belongs to another vocabulary");
                    }
                }).replaceWithVoid();
    }
}
