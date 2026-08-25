package com.japaneselearning.vocabulary.service;

import com.japaneselearning.vocabulary.importer.VocabularyImporter;
import com.japaneselearning.vocabulary.importer.dto.ImportResult;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Path;

@ApplicationScoped
public class VocabularyService {

    private final VocabularyImporter vocabularyImporter;

    public VocabularyService(VocabularyImporter vocabularyImporter) {
        this.vocabularyImporter = vocabularyImporter;
    }

    @WithTransaction
    public Uni<ImportResult> importVocabulary(Path file) {
        return vocabularyImporter.importVocabulary(file);
    }
}