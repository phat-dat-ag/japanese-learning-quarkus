package com.japaneselearning.vocabulary.admin.service;

import com.japaneselearning.vocabulary.admin.dto.VocabularyMeaningUpdateRequest;
import com.japaneselearning.vocabulary.admin.dto.VocabularyMeaningResponse;
import com.japaneselearning.vocabulary.entity.VocabularyMeaning;
import com.japaneselearning.vocabulary.repository.VocabularyMeaningRepository;
import com.japaneselearning.vocabulary.logging.LogVocabularyOperation;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class VocabularyMeaningEditService {
    private final VocabularyEditPersistence persistence;
    private final VocabularyMeaningRepository vocabularyMeanings;

    public VocabularyMeaningEditService(
            VocabularyEditPersistence persistence,
            VocabularyMeaningRepository vocabularyMeanings
    ) {
        this.persistence = persistence;
        this.vocabularyMeanings = vocabularyMeanings;
    }

    @WithTransaction
    @LogVocabularyOperation("vocabulary.meanings.add")
    public Uni<List<VocabularyMeaningResponse>> addVocabularyMeanings(
            Long vocabularyId,
            List<VocabularyMeaningUpdateRequest> requests
    ) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> Multi.createFrom().iterable(requests)
                        .onItem().transformToUniAndConcatenate(
                                request -> addMeaning(vocabularyId, request)
                        )
                        .collect().asList()
                )
        );
    }

    @WithTransaction
    @LogVocabularyOperation("vocabulary.meaning.update")
    public Uni<VocabularyMeaningResponse> updateVocabularyMeaning(
            Long vocabularyId,
            Long meaningId,
            VocabularyMeaningUpdateRequest request
    ) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> persistence.requireFound(
                        vocabularyMeanings.findMeaningForVocabulary(vocabularyId, meaningId), "Meaning", meaningId)
                )
                .flatMap(meaning -> requireUniqueMeaning(vocabularyId, meaningId, request)
                        .map(ignored -> {
                            applyMeaningChanges(meaning, request);

                            return new VocabularyMeaningResponse(meaningId);
                        })
                )
        );
    }

    private Uni<VocabularyMeaningResponse> addMeaning(
            Long vocabularyId,
            VocabularyMeaningUpdateRequest request
    ) {
        return requireUniqueMeaning(vocabularyId, null, request)
                .chain(() -> {
                    VocabularyMeaning meaning = new VocabularyMeaning();
                    meaning.vocabularyId = vocabularyId;
                    applyMeaningChanges(meaning, request);

                    return vocabularyMeanings.persistAndFlush(meaning)
                            .map(savedMeaning -> new VocabularyMeaningResponse(savedMeaning.id));
                });
    }

    private Uni<Void> requireUniqueMeaning(
            Long vocabularyId,
            Long meaningId,
            VocabularyMeaningUpdateRequest request
    ) {
        return vocabularyMeanings.findByVocabularyAndLanguageAndMeaning(
                        vocabularyId, request.language(), request.meaning()
                )
                .invoke(existingMeaning -> {
                    if (existingMeaning != null && !existingMeaning.id.equals(meaningId)) {
                        throw VocabularyEditPersistence.conflict("Meaning already exists for this vocabulary");
                    }
                }).replaceWithVoid();
    }

    private void applyMeaningChanges(
            VocabularyMeaning meaning,
            VocabularyMeaningUpdateRequest request
    ) {
        meaning.languageCode = request.language();
        meaning.meaning = request.meaning();
        meaning.isPrimary = request.isPrimary();
        meaning.displayOrder = request.displayOrder();
    }
}
