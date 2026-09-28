package com.japaneselearning.vocabulary.admin.service;

import com.japaneselearning.vocabulary.admin.dto.KanjiEdit;
import com.japaneselearning.vocabulary.admin.dto.KanjiReadingEdit;
import com.japaneselearning.vocabulary.admin.dto.VocabularyEditResult;
import com.japaneselearning.vocabulary.entity.Kanji;
import com.japaneselearning.vocabulary.entity.KanjiReading;
import com.japaneselearning.vocabulary.repository.KanjiReadingRepository;
import com.japaneselearning.vocabulary.repository.KanjiRepository;
import com.japaneselearning.vocabulary.repository.VocabularyKanjiRepository;
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
            VocabularyKanjiRepository kanjiAssignments) {
        this.persistence = persistence;
        this.kanji = kanji;
        this.kanjiReadings = kanjiReadings;
        this.kanjiAssignments = kanjiAssignments;
    }

    @WithTransaction
    public Uni<List<VocabularyEditResult>> add(Long vocabularyId, List<KanjiEdit> requests) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> Multi.createFrom().iterable(requests)
                        .onItem().transformToUniAndConcatenate(request -> addKanjiAssignment(vocabularyId, request))
                        .collect().asList()));
    }

    @WithTransaction
    public Uni<VocabularyEditResult> update(Long vocabularyId, Long kanjiId, KanjiEdit request) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> requireAssignedKanjiForUpdate(vocabularyId, kanjiId))
                .flatMap(kanjiEntity -> updateAssignedKanji(vocabularyId, kanjiEntity, request)));
    }

    @WithTransaction
    public Uni<List<VocabularyEditResult>> addReadings(
            Long vocabularyId,
            Long kanjiId,
            List<KanjiReadingEdit> requests) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> requireAssignedKanjiForUpdate(vocabularyId, kanjiId))
                .call(() -> requireExclusiveKanjiAssignment(vocabularyId, kanjiId))
                .chain(() -> Multi.createFrom().iterable(requests).onItem().transformToUniAndConcatenate(request ->
                        requireUniqueKanjiReading(kanjiId, null, request).chain(() -> {
                            KanjiReading reading = new KanjiReading();
                            reading.kanjiId = kanjiId;
                            applyReadingChanges(reading, request);
                            return kanjiReadings.persistAndFlush(reading).map(saved -> new VocabularyEditResult(saved.id));
                        })).collect().asList()));
    }

    @WithTransaction
    public Uni<VocabularyEditResult> updateReading(
            Long vocabularyId,
            Long kanjiId,
            Long readingId,
            KanjiReadingEdit request) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> requireAssignedKanjiForUpdate(vocabularyId, kanjiId))
                .chain(() -> persistence.requireFound(
                        kanjiReadings.findReadingForKanji(kanjiId, readingId), "Kanji reading"))
                .call(() -> requireExclusiveKanjiAssignment(vocabularyId, kanjiId))
                .flatMap(reading -> requireUniqueKanjiReading(kanjiId, readingId, request).map(ignored -> {
                    applyReadingChanges(reading, request);
                    return new VocabularyEditResult(readingId);
                })));
    }

    private Uni<VocabularyEditResult> updateAssignedKanji(
            Long vocabularyId, Kanji kanjiEntity, KanjiEdit request) {
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
                                "Kanji is shared; vocabulary editing cannot change its metadata");
                    }
                    applyKanjiMetadata(kanjiEntity, request);
                })
                .chain(() -> kanjiAssignments.updateKanjiAssignmentOrder(
                        vocabularyId, kanjiEntity.id, request.displayOrder()))
                .replaceWith(new VocabularyEditResult(kanjiEntity.id));
    }

    private Uni<VocabularyEditResult> addKanjiAssignment(Long vocabularyId, KanjiEdit request) {
        return findOrCreateMatchingKanji(request)
                .flatMap(kanjiEntity -> kanjiAssignments.existsKanjiAssignment(vocabularyId, kanjiEntity.id)
                        .invoke(assigned -> {
                            if (assigned) {
                                throw VocabularyEditPersistence.conflict(
                                        "Kanji is already assigned to this vocabulary");
                            }
                        })
                        .chain(() -> kanjiAssignments.insert(vocabularyId, kanjiEntity.id, request.displayOrder()))
                        .replaceWith(new VocabularyEditResult(kanjiEntity.id)));
    }

    private Uni<Kanji> findOrCreateMatchingKanji(KanjiEdit request) {
        return kanji.findByCharacterForUpdate(request.character()).flatMap(existingKanji -> {
            if (existingKanji == null) {
                return createKanji(request);
            }
            if (kanjiMetadataChanged(existingKanji, request)) {
                throw VocabularyEditPersistence.conflict(
                        "Kanji already exists with different metadata; attaching it cannot overwrite shared data");
            }
            return Uni.createFrom().item(existingKanji);
        });
    }

    private Uni<Kanji> requireAssignedKanjiForUpdate(Long vocabularyId, Long kanjiId) {
        return kanjiAssignments.existsKanjiAssignment(vocabularyId, kanjiId).invoke(assigned -> {
            if (!assigned) {
                throw VocabularyEditPersistence.resourceNotFound("Kanji assignment");
            }
        }).chain(() -> persistence.requireFound(kanji.findKanjiByIdForUpdate(kanjiId), "Kanji"));
    }

    private Uni<Void> requireExclusiveKanjiAssignment(Long vocabularyId, Long kanjiId) {
        return kanjiAssignments.countOtherVocabularyAssignmentsForUpdate(kanjiId, vocabularyId)
                .invoke(otherVocabularyCount -> {
            if (otherVocabularyCount > 0) {
                throw VocabularyEditPersistence.conflict(
                        "Kanji is shared; vocabulary editing cannot change its readings");
            }
        }).replaceWithVoid();
    }

    private Uni<Void> requireUniqueKanjiReading(Long kanjiId, Long readingId, KanjiReadingEdit request) {
        return kanjiReadings.findByKanjiIdAndReadingAndType(kanjiId, request.reading(), request.readingType())
                .invoke(existingReading -> {
                    if (existingReading != null && !existingReading.id.equals(readingId)) {
                        throw VocabularyEditPersistence.conflict("Reading already exists for this kanji");
                    }
                }).replaceWithVoid();
    }

    private Uni<Kanji> createKanji(KanjiEdit request) {
        Kanji kanjiEntity = new Kanji();
        applyKanjiMetadata(kanjiEntity, request);
        return kanji.persistAndFlush(kanjiEntity);
    }

    private boolean kanjiMetadataChanged(Kanji kanjiEntity, KanjiEdit request) {
        return !Objects.equals(kanjiEntity.character, request.character())
                || !Objects.equals(kanjiEntity.strokeCount, request.strokeCount())
                || !Objects.equals(kanjiEntity.meaningVi, request.meaningVi())
                || !Objects.equals(kanjiEntity.meaningEn, request.meaningEn());
    }

    private void applyKanjiMetadata(Kanji kanjiEntity, KanjiEdit request) {
        kanjiEntity.character = request.character();
        kanjiEntity.strokeCount = request.strokeCount();
        kanjiEntity.meaningVi = request.meaningVi();
        kanjiEntity.meaningEn = request.meaningEn();
    }

    private void applyReadingChanges(KanjiReading reading, KanjiReadingEdit request) {
        reading.reading = request.reading();
        reading.readingType = request.readingType();
        reading.displayOrder = request.displayOrder();
    }
}
