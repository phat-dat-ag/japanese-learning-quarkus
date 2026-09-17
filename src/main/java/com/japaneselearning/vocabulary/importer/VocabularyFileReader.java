package com.japaneselearning.vocabulary.importer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class VocabularyFileReader {

    private final ObjectMapper objectMapper;

    public VocabularyFileReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<VocabularyImportItem> read(Path file) throws IOException {
        return objectMapper.readValue(file.toFile(), new TypeReference<List<VocabularyImportItem>>() {
        });
    }

    public List<VocabularyImportItem> read(String resourcePath) {
        try {
            InputStream inputStream = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream(resourcePath);

            if (inputStream == null) {
                throw new IllegalArgumentException(
                        "Vocabulary file not found: " + resourcePath
                );
            }

            return objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<VocabularyImportItem>>() {
                    }
            );

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to read vocabulary file: " + resourcePath, e
            );
        }
    }
}
