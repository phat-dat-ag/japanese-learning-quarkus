package com.japaneselearning.vocabulary.importer;

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
        } catch (IOException e) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException("Invalid vocabulary JSON file", e)
            );
        }

        try {
            validator.validate(items);
        } catch (IllegalArgumentException e) {
            return Uni.createFrom().failure(e);
        }

        return Multi.createFrom().iterable(items)
                .onItem().transformToUniAndConcatenate(this::importItem)
                .collect().asList()
                .map(results -> summarize(items.size(), results));
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
