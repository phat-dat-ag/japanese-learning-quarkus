package com.japaneselearning.vocabulary.service;

import com.japaneselearning.vocabulary.importer.VocabularyImporter;
import com.japaneselearning.vocabulary.importer.dto.ImportResult;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.logging.LogVocabularyOperation;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class VocabularyService {

    private final VocabularyImporter vocabularyImporter;

    public VocabularyService(VocabularyImporter vocabularyImporter) {
        this.vocabularyImporter = vocabularyImporter;
    }

    @WithTransaction
    @LogVocabularyOperation(
            value = "vocabulary.import",
            description = "Vocabulary import",
            successMessage = "Vocabulary import completed: processed={total}, created={created}, updated={updated}; updated counts existing entries processed"
    )
    public Uni<ImportResult> importVocabulary(List<VocabularyImportItem> items) {
        return vocabularyImporter.importVocabulary(items);
    }

    @WithTransaction
    @LogVocabularyOperation(
            value = "vocabulary.import",
            description = "Vocabulary import",
            successMessage = "Vocabulary import completed: processed={total}, created={created}, updated={updated}; updated counts existing entries processed"
    )
    public Uni<ImportResult> importVocabulary(Path file) {
        return vocabularyImporter.importVocabulary(file);
    }
}