package com.japaneselearning.vocabulary.admin.service;

import com.japaneselearning.vocabulary.admin.dto.PitchAccentEdit;
import com.japaneselearning.vocabulary.admin.dto.VocabularyEditResult;
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
    private final VocabularyPitchAccentRepository repository;
    private final VocabularyReadingRepository readings;

    public VocabularyPitchAccentEditService(
            VocabularyEditPersistence persistence,
            VocabularyPitchAccentRepository repository,
            VocabularyReadingRepository readings) {
        this.persistence = persistence;
        this.repository = repository;
        this.readings = readings;
    }

    @WithTransaction
    public Uni<List<VocabularyEditResult>> add(Long vocabularyId, List<PitchAccentEdit> requests) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> Multi.createFrom().iterable(requests)
                        .onItem().transformToUniAndConcatenate(request -> addPitchAccent(vocabularyId, request))
                        .collect().asList()));
    }

    @WithTransaction
    public Uni<VocabularyEditResult> update(Long vocabularyId, Long pitchAccentId, PitchAccentEdit request) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> persistence.requireFound(
                        repository.findPitchAccentForVocabulary(vocabularyId, pitchAccentId), "Pitch accent"))
                .flatMap(pitchAccent -> validateReadingOwnershipAndUniqueness(vocabularyId, pitchAccentId, request)
                        .map(ignored -> {
                            applyPitchAccentChanges(pitchAccent, request);
                            return new VocabularyEditResult(pitchAccentId);
                        })));
    }

    private Uni<VocabularyEditResult> addPitchAccent(Long vocabularyId, PitchAccentEdit request) {
        return validateReadingOwnershipAndUniqueness(vocabularyId, null, request)
                .chain(() -> {
                    VocabularyPitchAccent pitchAccent = new VocabularyPitchAccent();
                    applyPitchAccentChanges(pitchAccent, request);
                    return repository.persistAndFlush(pitchAccent)
                            .map(savedPitchAccent -> new VocabularyEditResult(savedPitchAccent.id));
                });
    }

    private Uni<Void> validateReadingOwnershipAndUniqueness(
            Long vocabularyId,
            Long pitchAccentId,
            PitchAccentEdit request) {
        return persistence.requireFound(readings.findReadingForVocabulary(vocabularyId, request.readingId()), "Reading")
                .chain(() -> repository.findByReadingAndAccentPattern(request.readingId(), request.accentPattern()))
                .invoke(existing -> {
                    if (existing != null && !existing.id.equals(pitchAccentId)) {
                        throw VocabularyEditPersistence.conflict("Pitch accent already exists for this reading");
                    }
                }).replaceWithVoid();
    }

    private void applyPitchAccentChanges(VocabularyPitchAccent pitchAccent, PitchAccentEdit request) {
        pitchAccent.vocabularyReadingId = request.readingId();
        pitchAccent.accentPattern = request.accentPattern();
    }
}
