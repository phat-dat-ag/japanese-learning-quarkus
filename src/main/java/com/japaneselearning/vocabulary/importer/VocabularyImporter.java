package com.japaneselearning.vocabulary.importer;

import com.japaneselearning.common.logging.StructuredLog;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.japaneselearning.common.exception.ValidationError;
import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.vocabulary.importer.dto.ImportResult;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class VocabularyImporter {

    private static final Logger LOG = Logger.getLogger(VocabularyImporter.class);
    private final int maxBatchSize;
    private final VocabularyFileReader fileReader;
    private final VocabularyImportValidator validator;
    private final VocabularyCoreImporter coreImporter;
    private final VocabularyRelationImporter relationImporter;
    private final VocabularyAssignmentValidator assignmentValidator;
    private final VocabularyExampleImporter exampleImporter;

    public VocabularyImporter(
            VocabularyFileReader fileReader,
            VocabularyImportValidator validator,
            VocabularyCoreImporter coreImporter,
            VocabularyRelationImporter relationImporter,
            VocabularyAssignmentValidator assignmentValidator,
            VocabularyExampleImporter exampleImporter,
            @ConfigProperty(name = "vocabulary.import.max-batch-size", defaultValue = "500") int maxBatchSize
    ) {
        if (maxBatchSize < 1) {
            throw new IllegalArgumentException("vocabulary.import.max-batch-size must be positive");
        }

        this.maxBatchSize = maxBatchSize;
        this.fileReader = fileReader;
        this.validator = validator;
        this.coreImporter = coreImporter;
        this.relationImporter = relationImporter;
        this.assignmentValidator = assignmentValidator;
        this.exampleImporter = exampleImporter;
    }

    public Uni<ImportResult> importVocabulary(Path file) {
        return fileReader.readAsync(file)
                .onSubscription().invoke(() -> StructuredLog.event(
                        LOG,
                        Logger.Level.DEBUG,
                        "vocabulary.import.source",
                        "Reading vocabulary import JSON from the uploaded file",
                        Map.of(
                                "operation", "vocabulary.import",
                                "reason", "file"
                        )
                ))
                .invoke(items -> StructuredLog.event(
                        LOG,
                        Logger.Level.DEBUG,
                        "vocabulary.import.parsed",
                        "Parsed vocabulary import JSON; validating the record structure",
                        Map.of(
                                "operation", "vocabulary.import",
                                "total", items == null ? 0 : items.size()
                        )
                ))
                .onFailure(JsonProcessingException.class).transform(failure ->
                        invalidInputException("file", "File must contain valid vocabulary JSON"))
                .chain(items -> importVocabulary(items, "file"));
    }

    public Uni<ImportResult> importVocabulary(List<VocabularyImportItem> items) {
        return importVocabulary(items, "items");
    }

    private Uni<ImportResult> importVocabulary(List<VocabularyImportItem> items, String field) {
        if (items != null && items.size() > maxBatchSize) {
            return Uni.createFrom().failure(invalidInputException(field,
                    "Vocabulary batch must contain at most " + maxBatchSize + " items"));
        }

        try {
            validator.validate(items);
        } catch (IllegalArgumentException e) {
            String message = field.equals("file")
                    ? "Vocabulary file contains invalid data" : "Vocabulary batch contains invalid data";
            return Uni.createFrom().failure(invalidInputException(field, message));
        }

        return relationImporter.validateLessons(items)
                .onSubscription().invoke(() -> StructuredLog.event(
                        LOG,
                        Logger.Level.DEBUG,
                        "vocabulary.import.validated",
                        "Validated vocabulary structure, supplied normalized words and duplicate input words; checking lesson references",
                        Map.of(
                                "operation", "vocabulary.import",
                                "total", items.size(),
                                "reason", "structure"
                        )
                ))
                .chain(() -> validateExistingAssignments(items))
                .invoke(() -> StructuredLog.event(
                        LOG,
                        Logger.Level.DEBUG,
                        "vocabulary.import.validated",
                        "Validated lesson references and existing assignments; persisting the vocabulary batch atomically",
                        Map.of(
                                "operation", "vocabulary.import",
                                "total", items.size(),
                                "reason", "assignments"
                        )
                ))
                .chain(() -> Multi.createFrom().iterable(items)
                        .onItem().transformToUniAndConcatenate(this::importItem)
                        .collect().asList()
                        .map(results -> summarize(items.size(), results)));
    }

    private ValidationException invalidInputException(String field, String message) {
        return new ValidationException("VALIDATION_ERROR",
                "Invalid vocabulary import", List.of(new ValidationError(field, message)));
    }

    private Uni<Void> validateExistingAssignments(List<VocabularyImportItem> items) {
        return Multi.createFrom().iterable(items)
                .onItem().transformToUniAndConcatenate(item -> coreImporter.findExisting(item.normalizedWord)
                        .flatMap(existing -> existing == null
                                ? Uni.createFrom().voidItem()
                                : assignmentValidator.validate(existing, item)))
                .collect().asList().replaceWithVoid();
    }

    private Uni<ImportStatus> importItem(VocabularyImportItem item) {
        // Re-read after prevalidation: an earlier item may share this canonical identity.
        return coreImporter.findExisting(item.normalizedWord).flatMap(existing -> {
            if (existing != null) {
                StructuredLog.event(
                        LOG,
                        Logger.Level.DEBUG,
                        "vocabulary.import.existing",
                        "Existing normalized vocabulary entry found; preserving core data and checking assignments before appending examples",
                        Map.of(
                                "operation", "vocabulary.import",
                                "vocabularyId", existing.id
                        )
                );

                return assignmentValidator.validate(existing, item)
                        .chain(() -> exampleImporter.importExamples(existing, item))
                        .replaceWith(ImportStatus.UPDATED);
            }
            return coreImporter.create(item)
                    .flatMap(vocabulary -> relationImporter.importRelations(vocabulary, item))
                    .replaceWith(ImportStatus.CREATED);
        });
    }

    private ImportResult summarize(int total, List<ImportStatus> results) {
        int created = (int) results.stream().filter(ImportStatus.CREATED::equals).count();
        int updated = (int) results.stream().filter(ImportStatus.UPDATED::equals).count();

        return new ImportResult(total, created, updated);
    }

    private enum ImportStatus {
        CREATED,
        UPDATED
    }
}
