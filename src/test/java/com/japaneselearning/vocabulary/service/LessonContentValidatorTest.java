package com.japaneselearning.vocabulary.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.vocabulary.dto.content.LessonContentDocument;
import com.japaneselearning.vocabulary.dto.content.LessonContentWriteRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LessonContentValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final LessonContentValidator validator = new LessonContentValidator();

    @Test
    void acceptsEveryBlockAndPreservesUnicodeOrderAndFormatting() throws Exception {
        var document = mapper.readTree(getClass().getResourceAsStream("/lesson-content-v1.json"));
        var request = parse(document.toString());

        validator.validate(request);
        assertEquals(9, request.content().blocks().size());

        var saved = LessonContentValidator.json().valueToTree(request.content());

        assertEquals(document, saved);
    }

    @Test
    void emptyDocumentAndWhitespaceInlineAreValid() throws Exception {
        validator.validate(parse("{\"blocks\":[]}"));
        validator.validate(parse("""
                {"blocks":[{"id":"p","type":"paragraph","inlines":[{"text":" ","marks":[]}]}]}
                """
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"blocks\":[null]}",
            "{\"blocks\":[{\"id\":\"x\",\"type\":\"video\"}]}",
            "{\"blocks\":[{\"id\":\"x\",\"type\":\"divider\",\"style\":\"color:red\"}]}",
            "{\"blocks\":[{\"id\":\"x\",\"type\":\"divider\"},{\"id\":\"x\",\"type\":\"divider\"}]}",
            "{\"blocks\":[{\"id\":\"x\",\"type\":\"heading\",\"level\":7,\"inlines\":[]}]}",
            "{\"blocks\":[{\"id\":\"x\",\"type\":\"heading\",\"level\":\"2\",\"inlines\":[]}]}",
            "{\"blocks\":[{\"id\":\"x\",\"type\":\"list\",\"ordered\":true,\"items\":[null]}]}",
            "{\"blocks\":[{\"id\":\"x\",\"type\":\"list\",\"ordered\":true,\"items\":[]}]}",
            "{\"blocks\":[{\"id\":\"x\",\"type\":\"table\",\"headers\":[],\"rows\":[[],[]]}]}",
            "{\"blocks\":[{\"id\":\"x\",\"type\":\"example\",\"japanese\":\"日本\",\"reading\":\"にほん\",\"translations\":{\"en\":\"Japan\"}}]}",
            "{\"blocks\":[],\"html\":\"script\"}",
            "{\"blocks\":[],\"blocks\":[]}",
            "{\"blocks\":[{\"id\":\"x\",\"type\":\"callout\",\"variant\":\"custom\",\"inlines\":[]}]}",
            "{\"blocks\":[{\"id\":\"x\",\"type\":\"paragraph\",\"inlines\":[{\"text\":\"x\",\"marks\":[\"script\"]}]}]}",
            "{\"blocks\":[{\"id\":\"x\",\"type\":\"paragraph\",\"inlines\":[{\"text\":\"x\",\"marks\":[\"bold\",\"bold\"]}]}]}",
            "{\"blocks\":[{\"id\":\"x\",\"type\":\"paragraph\",\"inlines\":[{\"text\":\"<script>alert(1)</script>\",\"marks\":[]}]}]}"
    })
    void rejectsInvalidDocuments(String content) {
        var failure = assertThrows(Exception.class, () -> validator.validate(parse(content)));

        assertTrue(failure instanceof ValidationException || failure instanceof JsonProcessingException,
                () -> "Unexpected validation failure category: " + failure.getClass().getName()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"note", "tip", "warning", "information"})
    void acceptsOnlyDocumentedCalloutVariants(String variant) throws Exception {
        validator.validate(parse("{\"blocks\":[{\"id\":\"x\",\"type\":\"callout\",\"variant\":\"" + variant
                + "\",\"inlines\":[{\"text\":\"Information\",\"marks\":[]}]}]}"
        ));
    }

    @Test
    void rejectsLimitsAndRaggedTables() throws Exception {
        var document = (ObjectNode) mapper.readTree(getClass().getResourceAsStream("/lesson-content-v1.json"));
        ((ArrayNode) document.at("/blocks/4/rows/0")).remove(0);

        assertThrows(ValidationException.class, () -> validator.validate(parse(document.toString())));

        var blocks = mapper.createArrayNode();

        for (int i = 0; i <= LessonContentValidator.MAX_BLOCKS; i++) {
            blocks.addObject().put("id", "b" + i).put("type", "divider");
        }

        assertThrows(ValidationException.class, () -> validator.validate(parse("{\"blocks\":" + blocks + "}")));
        assertThrows(Exception.class, () -> parse("{\"blocks\":[],\"nested\":" + "[".repeat(20) + "0" + "]".repeat(20) + "}"));
        assertThrows(Exception.class, () -> parse("{\"blocks\":[],\"extra\":\"" + "あ".repeat(1024 * 1024) + "\"}"));

        var longText = new LessonContentDocument.Paragraph(
                "p",
                List.of(new LessonContentDocument.Inline(
                        "x".repeat(10001), List.of()
                ))
        );

        assertThrows(
                ValidationException.class,
                () -> validator.validate(new LessonContentWriteRequest(
                        1,
                        0L,
                        new LessonContentDocument(List.of(longText))
                ))
        );
    }

    @Test
    void schemaAndRevisionAreSeparateAndStrictlyTyped() {
        for (String body : List.of("null", "[]", "{", "{\"schemaVersion\":1,\"expectedVersion\":1.5,\"content\":{\"blocks\":[]}}")) {
            assertThrows(Exception.class, () -> validator.validate(mapper.readValue(body, LessonContentWriteRequest.class)));
        }

        var failure = assertThrows(ValidationException.class, () -> validator.validate(
                new LessonContentWriteRequest(2, 0L, new LessonContentDocument(List.of()))));

        assertEquals("LESSON_CONTENT_SCHEMA_UNSUPPORTED", failure.getCode());
    }

    @Test
    void rejectsAnOtherwiseValidDocumentThatExceedsEncodedSize() {
        var blocks = new java.util.ArrayList<LessonContentDocument.Block>();

        for (int i = 0; i < 200; i++) {
            blocks.add(new LessonContentDocument.Paragraph("p" + i,
                    List.of(new LessonContentDocument.Inline("日".repeat(2000), List.of()))));
        }

        var failure = assertThrows(jakarta.ws.rs.WebApplicationException.class, () -> validator.validate(
                new LessonContentWriteRequest(1, 0L, new LessonContentDocument(blocks))));

        assertEquals(413, failure.getResponse().getStatus());
    }

    private LessonContentWriteRequest parse(String content) throws Exception {
        return mapper.readValue("{\"schemaVersion\":1,\"expectedVersion\":0,\"content\":" + content + "}",
                LessonContentWriteRequest.class);
    }
}
