package com.japaneselearning.vocabulary.admin.service;

import com.japaneselearning.vocabulary.admin.dto.MeaningEdit;
import com.japaneselearning.vocabulary.admin.dto.VocabularyEditResult;
import com.japaneselearning.vocabulary.entity.VocabularyMeaning;
import com.japaneselearning.vocabulary.repository.VocabularyMeaningRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class VocabularyMeaningEditService {
    private final VocabularyEditPersistence persistence;
    private final VocabularyMeaningRepository repository;

    public VocabularyMeaningEditService(
            VocabularyEditPersistence persistence,
            VocabularyMeaningRepository repository) {
        this.persistence = persistence;
        this.repository = repository;
    }

    @WithTransaction
    public Uni<List<VocabularyEditResult>> add(Long vocabularyId, List<MeaningEdit> requests) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> Multi.createFrom().iterable(requests)
                        .onItem().transformToUniAndConcatenate(request -> addMeaning(vocabularyId, request))
                        .collect().asList()));
    }

    @WithTransaction
    public Uni<VocabularyEditResult> update(Long vocabularyId, Long meaningId, MeaningEdit request) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> persistence.requireFound(
                        repository.findMeaningForVocabulary(vocabularyId, meaningId), "Meaning"))
                .flatMap(meaning -> requireUniqueMeaning(vocabularyId, meaningId, request)
                        .map(ignored -> {
                            applyMeaningChanges(meaning, request);
                            return new VocabularyEditResult(meaningId);
                        })));
    }

    private Uni<VocabularyEditResult> addMeaning(Long vocabularyId, MeaningEdit request) {
        return requireUniqueMeaning(vocabularyId, null, request)
                .chain(() -> {
                    VocabularyMeaning meaning = new VocabularyMeaning();
                    meaning.vocabularyId = vocabularyId;
                    applyMeaningChanges(meaning, request);
                    return repository.persistAndFlush(meaning)
                            .map(savedMeaning -> new VocabularyEditResult(savedMeaning.id));
                });
    }

    private Uni<Void> requireUniqueMeaning(Long vocabularyId, Long meaningId, MeaningEdit request) {
        return repository.findByVocabularyAndLanguageAndMeaning(vocabularyId, request.language(), request.meaning())
                .invoke(existingMeaning -> {
                    if (existingMeaning != null && !existingMeaning.id.equals(meaningId)) {
                        throw VocabularyEditPersistence.conflict("Meaning already exists for this vocabulary");
                    }
                }).replaceWithVoid();
    }

    private void applyMeaningChanges(VocabularyMeaning meaning, MeaningEdit request) {
        meaning.languageCode = request.language();
        meaning.meaning = request.meaning();
        meaning.isPrimary = request.isPrimary();
        meaning.displayOrder = request.displayOrder();
    }
}
