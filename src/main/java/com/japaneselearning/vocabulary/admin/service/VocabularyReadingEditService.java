package com.japaneselearning.vocabulary.admin.service;

import com.japaneselearning.vocabulary.admin.dto.VocabularyReadingEdit;
import com.japaneselearning.vocabulary.admin.dto.VocabularyReadingResult;
import com.japaneselearning.vocabulary.entity.VocabularyReading;
import com.japaneselearning.vocabulary.repository.VocabularyReadingRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class VocabularyReadingEditService {
    private static final long NO_READING_EXCLUDED = -1L;

    private final VocabularyEditPersistence persistence;
    private final VocabularyReadingRepository vocabularyReadings;

    public VocabularyReadingEditService(
            VocabularyEditPersistence persistence,
            VocabularyReadingRepository vocabularyReadings) {
        this.persistence = persistence;
        this.vocabularyReadings = vocabularyReadings;
    }

    @WithTransaction
    public Uni<List<VocabularyReadingResult>> addVocabularyReadings(
            Long vocabularyId,
            List<VocabularyReadingEdit> requests
    ) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .call(() -> requirePrimaryReading(vocabularyId, NO_READING_EXCLUDED,
                        requests.stream().anyMatch(VocabularyReadingEdit::isPrimary)))
                .chain(() -> Multi.createFrom().iterable(requests)
                        .onItem().transformToUniAndConcatenate(request -> addReading(vocabularyId, request))
                        .collect().asList()));
    }

    @WithTransaction
    public Uni<VocabularyReadingResult> updateVocabularyReading(
            Long vocabularyId,
            Long readingId,
            VocabularyReadingEdit request
    ) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> persistence.requireFound(
                        vocabularyReadings.findReadingForVocabulary(vocabularyId, readingId), "Reading"))
                .flatMap(reading -> requireUniqueReading(vocabularyId, readingId, request)
                        .call(() -> requirePrimaryReading(vocabularyId, readingId, request.isPrimary()))
                        .map(ignored -> {
                            applyReadingChanges(reading, request);
                            return new VocabularyReadingResult(readingId);
                        })));
    }

    private Uni<Void> requirePrimaryReading(
            Long vocabularyId,
            Long excludedReadingId,
            boolean requestIncludesPrimary
    ) {
        if (requestIncludesPrimary) {
            return Uni.createFrom().voidItem();
        }
        return vocabularyReadings.countPrimaryReadingsExcluding(vocabularyId, excludedReadingId)
                .invoke(primaryCount -> {
                    if (primaryCount == 0) {
                        throw VocabularyEditPersistence.invalidRequest(
                                "isPrimary", "Vocabulary must retain at least one primary reading");
                    }
                }).replaceWithVoid();
    }

    private Uni<VocabularyReadingResult> addReading(Long vocabularyId, VocabularyReadingEdit request) {
        return requireUniqueReading(vocabularyId, null, request)
                .chain(() -> {
                    VocabularyReading reading = new VocabularyReading();
                    reading.vocabularyId = vocabularyId;
                    applyReadingChanges(reading, request);
                    return vocabularyReadings.persistAndFlush(reading)
                            .map(savedReading -> new VocabularyReadingResult(savedReading.id));
                });
    }

    private Uni<Void> requireUniqueReading(
            Long vocabularyId,
            Long readingId,
            VocabularyReadingEdit request
    ) {
        return vocabularyReadings.findByVocabularyIdAndReading(vocabularyId, request.reading()).invoke(existing -> {
            if (existing != null && !existing.id.equals(readingId)) {
                throw VocabularyEditPersistence.conflict("Reading already exists for this vocabulary");
            }
        }).replaceWithVoid();
    }

    private void applyReadingChanges(VocabularyReading reading, VocabularyReadingEdit request) {
        reading.reading = request.reading();
        reading.isPrimary = request.isPrimary();
        reading.displayOrder = request.displayOrder();
    }
}
