package com.japaneselearning.vocabulary.admin.service;

import com.japaneselearning.vocabulary.admin.dto.VocabularyPitchAccentEdit;
import com.japaneselearning.vocabulary.admin.dto.VocabularyPitchAccentResult;
import com.japaneselearning.vocabulary.entity.VocabularyPitchAccent;
import com.japaneselearning.vocabulary.repository.VocabularyPitchAccentRepository;
import com.japaneselearning.vocabulary.repository.VocabularyReadingRepository;
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
            VocabularyReadingRepository readings) {
        this.persistence = persistence;
        this.pitchAccents = pitchAccents;
        this.readings = readings;
    }

    @WithTransaction
    public Uni<List<VocabularyPitchAccentResult>> addVocabularyPitchAccents(
            Long vocabularyId,
            List<VocabularyPitchAccentEdit> requests
    ) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> Multi.createFrom().iterable(requests)
                        .onItem().transformToUniAndConcatenate(request -> addPitchAccent(vocabularyId, request))
                        .collect().asList()));
    }

    @WithTransaction
    public Uni<VocabularyPitchAccentResult> updateVocabularyPitchAccent(
            Long vocabularyId,
            Long pitchAccentId,
            VocabularyPitchAccentEdit request
    ) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> persistence.requireFound(
                        pitchAccents.findPitchAccentForVocabulary(vocabularyId, pitchAccentId), "Pitch accent"))
                .flatMap(pitchAccent -> validateReadingOwnershipAndUniqueness(vocabularyId, pitchAccentId, request)
                        .map(ignored -> {
                            applyPitchAccentChanges(pitchAccent, request);
                            return new VocabularyPitchAccentResult(pitchAccentId);
                        })));
    }

    private Uni<VocabularyPitchAccentResult> addPitchAccent(
            Long vocabularyId,
            VocabularyPitchAccentEdit request
    ) {
        return validateReadingOwnershipAndUniqueness(vocabularyId, null, request)
                .chain(() -> {
                    VocabularyPitchAccent pitchAccent = new VocabularyPitchAccent();
                    applyPitchAccentChanges(pitchAccent, request);
                    return pitchAccents.persistAndFlush(pitchAccent)
                            .map(savedPitchAccent -> new VocabularyPitchAccentResult(savedPitchAccent.id));
                });
    }

    private Uni<Void> validateReadingOwnershipAndUniqueness(
            Long vocabularyId,
            Long pitchAccentId,
            VocabularyPitchAccentEdit request
    ) {
        return persistence.requireFound(readings.findReadingForVocabulary(vocabularyId, request.readingId()), "Reading")
                .chain(() -> pitchAccents.findByReadingAndAccentPattern(request.readingId(), request.accentPattern()))
                .invoke(existing -> {
                    if (existing != null && !existing.id.equals(pitchAccentId)) {
                        throw VocabularyEditPersistence.conflict("Pitch accent already exists for this reading");
                    }
                }).replaceWithVoid();
    }

    private void applyPitchAccentChanges(
            VocabularyPitchAccent pitchAccent,
            VocabularyPitchAccentEdit request
    ) {
        pitchAccent.vocabularyReadingId = request.readingId();
        pitchAccent.accentPattern = request.accentPattern();
    }
}
