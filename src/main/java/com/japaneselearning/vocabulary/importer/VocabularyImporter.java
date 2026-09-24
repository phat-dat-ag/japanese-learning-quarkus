package com.japaneselearning.vocabulary.importer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.common.exception.ValidationError;
import com.japaneselearning.vocabulary.importer.dto.ImportResult;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class VocabularyImporter {

    private final VocabularyFileReader fileReader;
    private final VocabularyImportValidator validator;
    private final VocabularyCoreImporter coreImporter;
    private final VocabularyRelationImporter relationImporter;

    public VocabularyImporter(
            VocabularyFileReader fileReader,
            VocabularyImportValidator validator,
            VocabularyCoreImporter coreImporter,
            VocabularyRelationImporter relationImporter
    ) {
        this.fileReader = fileReader;
        this.validator = validator;
        this.coreImporter = coreImporter;
        this.relationImporter = relationImporter;
    }

    public Uni<ImportResult> importVocabulary(Path file) {
        final List<VocabularyImportItem> items;

        try {
            items = fileReader.read(file);
        } catch (JsonProcessingException e) {
            return invalidInput("File must contain valid vocabulary JSON");
        } catch (IOException e) {
            return Uni.createFrom().failure(e);
        }

        try {
            validator.validate(items);
        } catch (IllegalArgumentException e) {
            return invalidInput("Vocabulary file contains invalid data");
        }

        Uni<Void> validation = relationImporter.validateLessons(items);

        if (validation == null) {
            validation = Uni.createFrom().voidItem();
        }

        return validation.chain(() -> Multi.createFrom().iterable(items)
                .onItem().transformToUniAndConcatenate(this::importItem)
                .collect().asList()
                .map(results -> summarize(items.size(), results)));
    }

    private Uni<ImportResult> invalidInput(String message) {
        return Uni.createFrom().failure(new ValidationException("VALIDATION_ERROR",
                "Invalid vocabulary import", List.of(new ValidationError("file", message))));
    }

    private Uni<ImportStatus> importItem(VocabularyImportItem item) {
        return coreImporter.getOrCreate(item)
                .flatMap(vocabulary -> relationImporter.importRelations(vocabulary, item))
                .replaceWith(ImportStatus.UPDATED);
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
