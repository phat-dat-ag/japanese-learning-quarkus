package com.japaneselearning.vocabulary.admin.service;

import com.japaneselearning.vocabulary.admin.dto.KanjiUpdateRequest;
import com.japaneselearning.vocabulary.admin.dto.KanjiReadingUpdateRequest;
import com.japaneselearning.vocabulary.admin.dto.KanjiReadingResponse;
import com.japaneselearning.vocabulary.admin.dto.VocabularyKanjiResponse;
import com.japaneselearning.vocabulary.entity.Kanji;
import com.japaneselearning.vocabulary.entity.KanjiReading;
import com.japaneselearning.vocabulary.repository.KanjiReadingRepository;
import com.japaneselearning.vocabulary.repository.KanjiRepository;
import com.japaneselearning.vocabulary.repository.VocabularyKanjiRepository;
import com.japaneselearning.vocabulary.logging.LogVocabularyOperation;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class VocabularyKanjiEditService {
    private final VocabularyEditPersistence persistence;
    private final KanjiRepository kanji;
    private final KanjiReadingRepository kanjiReadings;
    private final VocabularyKanjiRepository kanjiAssignments;

    public VocabularyKanjiEditService(
            VocabularyEditPersistence persistence,
            KanjiRepository kanji,
            KanjiReadingRepository kanjiReadings,
            VocabularyKanjiRepository kanjiAssignments
    ) {
        this.persistence = persistence;
        this.kanji = kanji;
        this.kanjiReadings = kanjiReadings;
        this.kanjiAssignments = kanjiAssignments;
    }

    @WithTransaction
    @LogVocabularyOperation("vocabulary.kanji.add")
    public Uni<List<VocabularyKanjiResponse>> addVocabularyKanji(
            Long vocabularyId,
            List<KanjiUpdateRequest> requests
    ) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> Multi.createFrom().iterable(requests)
                        .onItem().transformToUniAndConcatenate(
                                request -> addKanjiAssignment(vocabularyId, request)
                        )
                        .collect().asList()
                )
        );
    }

    @WithTransaction
    @LogVocabularyOperation("vocabulary.kanji.update")
    public Uni<VocabularyKanjiResponse> updateVocabularyKanji(
            Long vocabularyId,
            Long kanjiId,
            KanjiUpdateRequest request
    ) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> requireAssignedKanjiForUpdate(vocabularyId, kanjiId))
                .flatMap(kanjiEntity -> updateAssignedKanji(vocabularyId, kanjiEntity, request)));
    }

    @WithTransaction
    @LogVocabularyOperation("vocabulary.kanji_readings.add")
    public Uni<List<KanjiReadingResponse>> addKanjiReadings(
            Long vocabularyId,
            Long kanjiId,
            List<KanjiReadingUpdateRequest> requests
    ) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> requireAssignedKanjiForUpdate(vocabularyId, kanjiId))
                .call(() -> requireExclusiveKanjiAssignment(vocabularyId, kanjiId))
                .chain(() -> Multi.createFrom().iterable(requests)
                        .onItem().transformToUniAndConcatenate(
                                request -> addKanjiReading(kanjiId, request)
                        )
                        .collect().asList()
                )
        );
    }

    @WithTransaction
    @LogVocabularyOperation("vocabulary.kanji_reading.update")
    public Uni<KanjiReadingResponse> updateKanjiReading(
            Long vocabularyId,
            Long kanjiId,
            Long readingId,
            KanjiReadingUpdateRequest request
    ) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> requireAssignedKanjiForUpdate(vocabularyId, kanjiId))
                .chain(() -> persistence.requireFound(
                        kanjiReadings.findReadingForKanji(kanjiId, readingId), "Kanji reading", readingId
                ))
                .call(() -> requireExclusiveKanjiAssignment(vocabularyId, kanjiId))
                .flatMap(reading -> requireUniqueKanjiReading(kanjiId, readingId, request)
                        .map(ignored -> {
                            applyKanjiReadingChanges(reading, request);
                            return new KanjiReadingResponse(readingId);
                        })
                )
        );
    }

    private Uni<KanjiReadingResponse> addKanjiReading(
            Long kanjiId,
            KanjiReadingUpdateRequest request
    ) {
        return requireUniqueKanjiReading(kanjiId, null, request)
                .chain(() -> {
                    KanjiReading reading = new KanjiReading();
                    reading.kanjiId = kanjiId;
                    applyKanjiReadingChanges(reading, request);
                    return kanjiReadings.persistAndFlush(reading)
                            .map(savedReading -> new KanjiReadingResponse(savedReading.id));
                });
    }

    private Uni<VocabularyKanjiResponse> updateAssignedKanji(
            Long vocabularyId,
            Kanji kanjiEntity,
            KanjiUpdateRequest request
    ) {
        return kanji.findByCharacter(request.character())
                .invoke(existingKanji -> {
                    if (existingKanji != null && !existingKanji.id.equals(kanjiEntity.id)) {
                        throw VocabularyEditPersistence.conflict("Kanji character already exists");
                    }
                })
                .chain(() -> kanjiAssignments.countOtherVocabularyAssignmentsForUpdate(kanjiEntity.id, vocabularyId))
                .invoke(otherVocabularyCount -> {
                    if (otherVocabularyCount > 0 && kanjiMetadataChanged(kanjiEntity, request)) {
                        throw VocabularyEditPersistence.conflict(
                                "Kanji is shared; vocabulary editing cannot change its metadata"
                        );
                    }
                    applyKanjiMetadata(kanjiEntity, request);
                })
                .chain(() -> kanjiAssignments.updateKanjiAssignmentOrder(
                        vocabularyId, kanjiEntity.id, request.displayOrder()
                ))
                .replaceWith(new VocabularyKanjiResponse(kanjiEntity.id));
    }

    private Uni<VocabularyKanjiResponse> addKanjiAssignment(
            Long vocabularyId,
            KanjiUpdateRequest request
    ) {
        return findOrCreateMatchingKanji(request)
                .flatMap(kanjiEntity -> kanjiAssignments.existsKanjiAssignment(vocabularyId, kanjiEntity.id)
                        .invoke(assigned -> {
                            if (assigned) {
                                throw VocabularyEditPersistence.conflict(
                                        "Kanji is already assigned to this vocabulary"
                                );
                            }
                        })
                        .chain(() -> kanjiAssignments.insert(
                                vocabularyId, kanjiEntity.id, request.displayOrder()
                        ))
                        .replaceWith(new VocabularyKanjiResponse(kanjiEntity.id))
                );
    }

    private Uni<Kanji> findOrCreateMatchingKanji(KanjiUpdateRequest request) {
        return kanji.findKanjiByCharacterForUpdate(request.character()).flatMap(existingKanji -> {
            if (existingKanji == null) {
                return createKanji(request);
            }

            if (kanjiMetadataChanged(existingKanji, request)) {
                throw VocabularyEditPersistence.conflict(
                        "Kanji already exists with different metadata; attaching it cannot overwrite shared data"
                );
            }

            return Uni.createFrom().item(existingKanji);
        });
    }

    private Uni<Kanji> requireAssignedKanjiForUpdate(
            Long vocabularyId,
            Long kanjiId
    ) {
        return kanjiAssignments.existsKanjiAssignment(vocabularyId, kanjiId).invoke(assigned -> {
            if (!assigned) {
                throw VocabularyEditPersistence.resourceNotFound("Kanji assignment", kanjiId);
            }
        }).chain(() -> persistence.requireFound(kanji.findKanjiByIdForUpdate(kanjiId), "Kanji", kanjiId));
    }

    private Uni<Void> requireExclusiveKanjiAssignment(
            Long vocabularyId,
            Long kanjiId
    ) {
        return kanjiAssignments.countOtherVocabularyAssignmentsForUpdate(kanjiId, vocabularyId)
                .invoke(otherVocabularyCount -> {
                    if (otherVocabularyCount > 0) {
                        throw VocabularyEditPersistence.conflict(
                                "Kanji is shared; vocabulary editing cannot change its readings"
                        );
                    }
                }).replaceWithVoid();
    }

    private Uni<Void> requireUniqueKanjiReading(
            Long kanjiId,
            Long readingId,
            KanjiReadingUpdateRequest request
    ) {
        return kanjiReadings.findByKanjiIdAndReadingAndType(kanjiId, request.reading(), request.readingType())
                .invoke(existingReading -> {
                    if (existingReading != null && !existingReading.id.equals(readingId)) {
                        throw VocabularyEditPersistence.conflict("Reading already exists for this kanji");
                    }
                }).replaceWithVoid();
    }

    private Uni<Kanji> createKanji(KanjiUpdateRequest request) {
        Kanji kanjiEntity = new Kanji();
        applyKanjiMetadata(kanjiEntity, request);

        return kanji.persistAndFlush(kanjiEntity);
    }

    private boolean kanjiMetadataChanged(Kanji kanjiEntity, KanjiUpdateRequest request) {
        return !Objects.equals(kanjiEntity.character, request.character())
                || !Objects.equals(kanjiEntity.strokeCount, request.strokeCount())
                || !Objects.equals(kanjiEntity.meaningVi, request.meaningVi())
                || !Objects.equals(kanjiEntity.meaningEn, request.meaningEn());
    }

    private void applyKanjiMetadata(Kanji kanjiEntity, KanjiUpdateRequest request) {
        kanjiEntity.character = request.character();
        kanjiEntity.strokeCount = request.strokeCount();
        kanjiEntity.meaningVi = request.meaningVi();
        kanjiEntity.meaningEn = request.meaningEn();
    }

    private void applyKanjiReadingChanges(
            KanjiReading reading,
            KanjiReadingUpdateRequest request
    ) {
        reading.reading = request.reading();
        reading.readingType = request.readingType();
        reading.displayOrder = request.displayOrder();
    }
}
