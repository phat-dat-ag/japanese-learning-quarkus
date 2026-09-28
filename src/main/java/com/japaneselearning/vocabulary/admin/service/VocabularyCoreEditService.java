package com.japaneselearning.vocabulary.admin.service;

import com.japaneselearning.vocabulary.admin.dto.CoreEdit;
import com.japaneselearning.vocabulary.admin.dto.VocabularyEditResult;
import com.japaneselearning.vocabulary.repository.VocabularyRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VocabularyCoreEditService {
    private final VocabularyEditPersistence persistence;
    private final VocabularyRepository repository;

    public VocabularyCoreEditService(VocabularyEditPersistence persistence, VocabularyRepository repository) {
        this.persistence = persistence;
        this.repository = repository;
    }

    @WithTransaction
    public Uni<VocabularyEditResult> update(Long vocabularyId, CoreEdit request) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .flatMap(vocabulary -> repository.findByNormalizedWord(request.normalizedWord())
                        .map(existingVocabulary -> {
                            if (existingVocabulary != null && !existingVocabulary.id.equals(vocabularyId)) {
                                throw VocabularyEditPersistence.conflict(
                                        "Normalized word already belongs to another vocabulary");
                            }
                            vocabulary.word = request.word();
                            vocabulary.normalizedWord = request.normalizedWord();
                            return new VocabularyEditResult(vocabularyId);
                        })));
    }
}
