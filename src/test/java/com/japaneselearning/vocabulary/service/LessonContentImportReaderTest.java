package com.japaneselearning.vocabulary.service;

import com.japaneselearning.common.exception.ValidationException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LessonContentImportReaderTest {
    @Test
    void documentedCompleteExampleMatchesTheAuthoritativeContract() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var reader = new LessonContentImportReader(
                    null,
                    factory.getValidator(),
                    new LessonContentValidator(),
                    5242880,
                    100
            );

            var items = reader.parse(Files.readAllBytes(Path.of("docs/grammar-lessons-import-example.json")));

            assertEquals(2, items.size());
            assertEquals("N5", items.get(0).level());
            assertEquals(2, items.get(1).lessonNumber());
        }
    }

    @Test
    void validatesEveryItemAndConfigurableLimitsBeforePersistence() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var reader = new LessonContentImportReader(
                    null,
                    factory.getValidator(),
                    new LessonContentValidator(),
                    4096,
                    1
            );

            String item = """
                    {"level":"N5","lessonNumber":1,"title":"は","displayOrder":1,
                     "schemaVersion":1,"expectedVersion":0,"content":{"blocks":[]}}
                    """;

            assertEquals(1, reader.parse(bytes("[" + item + "]")).size());
            assertThrows(ValidationException.class, () -> reader.parse(bytes("[" + item + "," + item + "]")));
            assertThrows(ValidationException.class, () -> reader.parse(bytes("[" + item.replace("\"N5\"", "null") + "]")));
            assertThrows(ValidationException.class, () -> reader.parse(bytes("[" + item.replace("\"blocks\":[]", "\"blocks\":[],\"html\":\"x\"") + "]")));
            assertThrows(ValidationException.class, () -> reader.parse(bytes("[null]")));
            assertThrows(ValidationException.class, () -> reader.parse(bytes("[]")));
            assertThrows(ValidationException.class, () -> reader.parse(bytes("[{}")));
            assertThrows(jakarta.ws.rs.WebApplicationException.class, () -> reader.parse(new byte[4097]));
        }
    }

    private byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
