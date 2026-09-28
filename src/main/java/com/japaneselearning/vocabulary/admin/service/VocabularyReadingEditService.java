package com.japaneselearning.vocabulary.admin.service;

import com.japaneselearning.vocabulary.admin.dto.ReadingEdit;
import com.japaneselearning.vocabulary.admin.dto.VocabularyEditResult;
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
    private final VocabularyReadingRepository repository;

    public VocabularyReadingEditService(
            VocabularyEditPersistence persistence,
            VocabularyReadingRepository repository) {
        this.persistence = persistence;
        this.repository = repository;
    }

    @WithTransaction
    public Uni<List<VocabularyEditResult>> add(Long vocabularyId, List<ReadingEdit> requests) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .call(() -> repository.countPrimaryReadingsExcluding(vocabularyId, NO_READING_EXCLUDED).invoke(count -> {
                    if (count == 0 && requests.stream().noneMatch(request -> request.isPrimary())) {
                        throw VocabularyEditPersistence.invalidRequest(
                                "isPrimary", "Vocabulary must retain at least one primary reading");
                    }
                }))
                .chain(() -> Multi.createFrom().iterable(requests)
                        .onItem().transformToUniAndConcatenate(request -> addReading(vocabularyId, request))
                        .collect().asList()));
    }

    @WithTransaction
    public Uni<VocabularyEditResult> update(Long vocabularyId, Long readingId, ReadingEdit request) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> persistence.requireFound(
                        repository.findReadingForVocabulary(vocabularyId, readingId), "Reading"))
                .flatMap(reading -> requireUniqueReading(vocabularyId, readingId, request)
                        .call(() -> repository.countPrimaryReadingsExcluding(vocabularyId, readingId).invoke(count -> {
                            if (!request.isPrimary() && count == 0) {
                                throw VocabularyEditPersistence.invalidRequest(
                                        "isPrimary", "Vocabulary must retain at least one primary reading");
                            }
                        }))
                        .map(ignored -> {
                            applyReadingChanges(reading, request);
                            return new VocabularyEditResult(readingId);
                        })));
    }

    private Uni<VocabularyEditResult> addReading(Long vocabularyId, ReadingEdit request) {
        return requireUniqueReading(vocabularyId, null, request)
                .chain(() -> {
                    VocabularyReading reading = new VocabularyReading();
                    reading.vocabularyId = vocabularyId;
                    applyReadingChanges(reading, request);
                    return repository.persistAndFlush(reading)
                            .map(savedReading -> new VocabularyEditResult(savedReading.id));
                });
    }

    private Uni<Void> requireUniqueReading(Long vocabularyId, Long readingId, ReadingEdit request) {
        return repository.findByVocabularyIdAndReading(vocabularyId, request.reading()).invoke(existing -> {
            if (existing != null && !existing.id.equals(readingId)) {
                throw VocabularyEditPersistence.conflict("Reading already exists for this vocabulary");
            }
        }).replaceWithVoid();
    }

    private void applyReadingChanges(VocabularyReading reading, ReadingEdit request) {
        reading.reading = request.reading();
        reading.isPrimary = request.isPrimary();
        reading.displayOrder = request.displayOrder();
    }
}
