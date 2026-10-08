package com.japaneselearning.vocabulary.admin.service;

import com.japaneselearning.vocabulary.admin.dto.VocabularyPitchAccentUpdateRequest;
import com.japaneselearning.vocabulary.admin.dto.VocabularyPitchAccentResponse;
import com.japaneselearning.vocabulary.entity.VocabularyPitchAccent;
import com.japaneselearning.vocabulary.repository.VocabularyPitchAccentRepository;
import com.japaneselearning.vocabulary.repository.VocabularyReadingRepository;
import com.japaneselearning.vocabulary.logging.LogVocabularyOperation;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class VocabularyPitchAccentEditService {
    private final VocabularyEditPersistence persistence;
    private final VocabularyPitchAccentRepository pitchAccents;
    private final VocabularyReadingRepository readings;

    public VocabularyPitchAccentEditService(
            VocabularyEditPersistence persistence,
            VocabularyPitchAccentRepository pitchAccents,
            VocabularyReadingRepository readings
    ) {
        this.persistence = persistence;
        this.pitchAccents = pitchAccents;
        this.readings = readings;
    }

    @WithTransaction
    @LogVocabularyOperation("vocabulary.pitch_accents.add")
    public Uni<List<VocabularyPitchAccentResponse>> addVocabularyPitchAccents(
            Long vocabularyId,
            List<VocabularyPitchAccentUpdateRequest> requests
    ) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> Multi.createFrom().iterable(requests)
                        .onItem().transformToUniAndConcatenate(
                                request -> addPitchAccent(vocabularyId, request)
                        )
                        .collect().asList()
                )
        );
    }

    @WithTransaction
    @LogVocabularyOperation("vocabulary.pitch_accent.update")
    public Uni<VocabularyPitchAccentResponse> updateVocabularyPitchAccent(
            Long vocabularyId,
            Long pitchAccentId,
            VocabularyPitchAccentUpdateRequest request
    ) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> persistence.requireFound(
                        pitchAccents.findPitchAccentForVocabulary(vocabularyId, pitchAccentId), "Pitch accent", pitchAccentId)
                )
                .flatMap(pitchAccent -> validateReadingOwnershipAndUniqueness(
                                vocabularyId, pitchAccentId, request
                        )
                                .map(ignored -> {
                                    applyPitchAccentChanges(pitchAccent, request);

                                    return new VocabularyPitchAccentResponse(pitchAccentId);
                                })
                )
        );
    }

    private Uni<VocabularyPitchAccentResponse> addPitchAccent(
            Long vocabularyId,
            VocabularyPitchAccentUpdateRequest request
    ) {
        return validateReadingOwnershipAndUniqueness(vocabularyId, null, request)
                .chain(() -> {
                    VocabularyPitchAccent pitchAccent = new VocabularyPitchAccent();
                    applyPitchAccentChanges(pitchAccent, request);

                    return pitchAccents.persistAndFlush(pitchAccent)
                            .map(savedPitchAccent ->
                                    new VocabularyPitchAccentResponse(savedPitchAccent.id)
                            );
                });
    }

    private Uni<Void> validateReadingOwnershipAndUniqueness(
            Long vocabularyId,
            Long pitchAccentId,
            VocabularyPitchAccentUpdateRequest request
    ) {
        return persistence.requireFound(readings.findReadingForVocabulary(
                        vocabularyId, request.readingId()), "Reading", request.readingId()
                )
                .chain(() -> pitchAccents.findByReadingAndAccentPattern(
                        request.readingId(), request.accentPattern()
                ))
                .invoke(existing -> {
                    if (existing != null && !existing.id.equals(pitchAccentId)) {
                        throw VocabularyEditPersistence.conflict("Pitch accent already exists for this reading");
                    }
                }).replaceWithVoid();
    }

    private void applyPitchAccentChanges(
            VocabularyPitchAccent pitchAccent,
            VocabularyPitchAccentUpdateRequest request
    ) {
        pitchAccent.vocabularyReadingId = request.readingId();
        pitchAccent.accentPattern = request.accentPattern();
    }
}
