package com.japaneselearning.vocabulary.admin.service;

import com.japaneselearning.vocabulary.admin.dto.VocabularyExampleUpdateRequest;
import com.japaneselearning.vocabulary.admin.dto.VocabularyExampleResponse;
import com.japaneselearning.vocabulary.entity.ExampleSentence;
import com.japaneselearning.vocabulary.entity.VocabularyExample;
import com.japaneselearning.vocabulary.repository.ExampleSentenceRepository;
import com.japaneselearning.vocabulary.repository.VocabularyExampleRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class VocabularyExampleEditService {
    private final VocabularyEditPersistence persistence;
    private final ExampleSentenceRepository sentences;
    private final VocabularyExampleRepository exampleAssignments;

    public VocabularyExampleEditService(
            VocabularyEditPersistence persistence,
            ExampleSentenceRepository sentences,
            VocabularyExampleRepository exampleAssignments
    ) {
        this.persistence = persistence;
        this.sentences = sentences;
        this.exampleAssignments = exampleAssignments;
    }

    @WithTransaction
    public Uni<List<VocabularyExampleResponse>> addVocabularyExamples(
            Long vocabularyId,
            List<VocabularyExampleUpdateRequest> requests
    ) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> Multi.createFrom().iterable(requests)
                        .onItem().transformToUniAndConcatenate(
                                request -> addExample(vocabularyId, request)
                        )
                        .collect().asList()
                )
        );
    }

    @WithTransaction
    public Uni<VocabularyExampleResponse> updateVocabularyExample(
            Long vocabularyId,
            Long exampleId,
            VocabularyExampleUpdateRequest request
    ) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> persistence.requireFound(
                        exampleAssignments.findExampleAssignment(vocabularyId, exampleId), "Example", exampleId)
                )
                .flatMap(assignment -> updateAssignedExample(
                        vocabularyId, exampleId, assignment, request
                ))
        );
    }

    private Uni<VocabularyExampleResponse> addExample(
            Long vocabularyId,
            VocabularyExampleUpdateRequest request
    ) {
        return requireUniqueExample(vocabularyId, null, request)
                .chain(() -> {
                    ExampleSentence sentence = new ExampleSentence();
                    applyExampleChanges(sentence, request);
                    return sentences.persistAndFlush(sentence)
                            .call(savedSentence -> exampleAssignments.insert(
                                    vocabularyId, savedSentence.id, request.targetText(), request.displayOrder()
                            ))
                            .map(savedSentence -> new VocabularyExampleResponse(savedSentence.id));
                });
    }

    private Uni<VocabularyExampleResponse> updateAssignedExample(
            Long vocabularyId,
            Long exampleId,
            VocabularyExample assignment,
            VocabularyExampleUpdateRequest request
    ) {
        return sentences.findSentenceByIdForUpdate(exampleId)
                .flatMap(sentence -> requireUniqueExample(vocabularyId, exampleId, request)
                        .chain(() -> exampleAssignments.countOtherVocabularyAssignments(exampleId, vocabularyId))
                        .map(otherVocabularyCount -> {
                            if (otherVocabularyCount > 0 && sentenceContentChanged(sentence, request)) {
                                throw VocabularyEditPersistence.conflict(
                                        "Example sentence is shared; vocabulary editing cannot change its content"
                                );
                            }
                            applyExampleChanges(sentence, request);
                            assignment.targetText = request.targetText();
                            assignment.displayOrder = request.displayOrder();
                            return new VocabularyExampleResponse(exampleId);
                        })
                );
    }

    private Uni<Void> requireUniqueExample(
            Long vocabularyId,
            Long excludedExampleId,
            VocabularyExampleUpdateRequest request
    ) {
        return exampleAssignments.findByVocabularyId(vocabularyId).invoke(existing -> {
            boolean duplicate = existing.stream().anyMatch(link ->
                    !link.exampleSentenceId.equals(excludedExampleId)
                            && Objects.equals(link.exampleSentence.japaneseText, request.japaneseText())
                            && Objects.equals(link.exampleSentence.japaneseReading, request.japaneseReading())
                            && Objects.equals(link.targetText, request.targetText())
            );

            if (duplicate) {
                throw VocabularyEditPersistence.conflict("Example already exists for this vocabulary");
            }
        }).replaceWithVoid();
    }

    private boolean sentenceContentChanged(
            ExampleSentence sentence,
            VocabularyExampleUpdateRequest request
    ) {
        return !Objects.equals(sentence.japaneseText, request.japaneseText())
                || !Objects.equals(sentence.japaneseReading, request.japaneseReading())
                || !Objects.equals(sentence.meaningVi, request.meaningVi())
                || !Objects.equals(sentence.meaningEn, request.meaningEn());
    }

    private void applyExampleChanges(
            ExampleSentence sentence,
            VocabularyExampleUpdateRequest request
    ) {
        sentence.japaneseText = request.japaneseText();
        sentence.japaneseReading = request.japaneseReading();
        sentence.meaningVi = request.meaningVi();
        sentence.meaningEn = request.meaningEn();
    }
}
