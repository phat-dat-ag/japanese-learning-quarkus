package com.japaneselearning.vocabulary.importer;

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

        LOG.debugf("Vocabulary import started total=%d", items.size());
        return relationImporter.validateLessons(items)
                .chain(() -> validateExistingAssignments(items))
                .chain(() -> Multi.createFrom().iterable(items)
                        .onItem().transformToUniAndConcatenate(this::importItem)
                        .collect().asList()
                        .map(results -> summarize(items.size(), results)))
                .invoke(result -> LOG.debugf("Vocabulary import processed total=%d created=%d updated=%d",
                        result.total(), result.created(), result.updated()));
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
