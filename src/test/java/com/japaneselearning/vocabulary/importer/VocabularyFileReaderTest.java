package com.japaneselearning.vocabulary.importer;

import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class VocabularyFileReaderTest {

    @Inject
    VocabularyFileReader vocabularyFileReader;

    @Test
    void shouldReadN5Vocabulary() {

        List<VocabularyImportItem> vocabulary =
                vocabularyFileReader.read(
                        "data/vocabulary/n5.json"
                );

        assertNotNull(vocabulary);
        assertEquals(1, vocabulary.size());

        VocabularyImportItem student = vocabulary.get(0);

        assertEquals("学生", student.word);
        assertEquals("N5", student.levels.get(0));
        assertEquals("がくせい",
                student.readings.get(0).reading);
    }
}