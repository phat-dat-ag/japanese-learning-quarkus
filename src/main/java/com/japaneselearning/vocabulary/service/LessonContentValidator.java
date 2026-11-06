package com.japaneselearning.vocabulary.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.japaneselearning.common.exception.ValidationError;
import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.common.logging.StructuredLog;
import com.japaneselearning.vocabulary.dto.content.LessonContentDocument.Heading;
import com.japaneselearning.vocabulary.dto.content.LessonContentDocument.ListBlock;
import com.japaneselearning.vocabulary.dto.content.LessonContentDocument.Block;
import com.japaneselearning.vocabulary.dto.content.LessonContentDocument.Paragraph;
import com.japaneselearning.vocabulary.dto.content.LessonContentDocument.Table;
import com.japaneselearning.vocabulary.dto.content.LessonContentDocument.Inline;
import com.japaneselearning.vocabulary.dto.content.LessonContentDocument.Example;
import com.japaneselearning.vocabulary.dto.content.LessonContentDocument.Callout;
import com.japaneselearning.vocabulary.dto.content.LessonContentDocument.Quote;
import com.japaneselearning.vocabulary.dto.content.LessonContentWriteRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import org.jboss.logging.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

@ApplicationScoped
public class LessonContentValidator {
    private static final Logger LOG = Logger.getLogger(LessonContentValidator.class);
    public static final int MAX_DOCUMENT_BYTES = 1024 * 1024;
    public static final int MAX_DEPTH = 12;
    public static final int MAX_BLOCKS = 200;
    public static final int MAX_TEXT_LENGTH = 10_000;
    private static final Set<String> MARKS = Set.of("bold", "italic", "underline");
    private static final Set<String> VARIANTS = Set.of("note", "tip", "warning", "information");
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .build();

    public static ObjectMapper json() {
        return JSON;
    }

    public void validate(LessonContentWriteRequest request) {
        if (request == null
                || request.schemaVersion() == null
                || request.expectedVersion() == null
                || request.expectedVersion() < 0
                || request.content() == null
        ) {
            throw invalid(
                    "request",
                    "schemaVersion, nonnegative expectedVersion and content are required"
            );
        }

        if (request.schemaVersion() != 1) {
            throw unsupported();
        }

        try {
            if (JSON.writeValueAsBytes(request.content()).length > MAX_DOCUMENT_BYTES) {
                throw new WebApplicationException(413);
            }
        } catch (JsonProcessingException failure) {
            throw invalid("content", "Document cannot be encoded");
        }

        var blocks = request.content().blocks();

        array(blocks, 0, MAX_BLOCKS, "content.blocks");

        Set<String> ids = new HashSet<>();

        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            String path = "content.blocks[" + i + "]";

            if (block.id() == null
                    || !block.id().matches("[A-Za-z0-9_-]{1,64}")
                    || !ids.add(block.id())
            ) {
                throw invalid(
                        path + ".id",
                        "Block IDs must be unique ASCII identifiers of 1–64 characters"
                );
            }

            if (block instanceof Heading heading) {
                if (heading.level() == null
                        || heading.level() < 1
                        || heading.level() > 6
                ) {
                    throw invalid(
                            path + ".level",
                            "Heading level must be between 1 and 6"
                    );
                }

                inlines(heading.inlines(), path + ".inlines");
            } else if (block instanceof Paragraph paragraph) {
                inlines(paragraph.inlines(), path + ".inlines");
            } else if (block instanceof ListBlock list) {
                if (list.ordered() == null) {
                    throw invalid(path + ".ordered", "List ordering is required");
                }

                array(list.items(), 1, 100, path + ".items");

                for (var item : list.items()) {
                    inlines(item, path + ".items");
                }
            } else if (block instanceof Table table) {
                table(table, path);
            } else if (block instanceof Example example) {
                text(example.japanese(), path + ".japanese");
                text(example.reading(), path + ".reading");

                if (example.translations() == null) {
                    throw invalid(path + ".translations", "Translations are required");
                }

                text(example.translations().en(), path + ".translations.en");
                text(example.translations().vi(), path + ".translations.vi");
            } else if (block instanceof Callout callout) {
                if (callout.variant() == null || !VARIANTS.contains(callout.variant())) {
                    throw invalid(path + ".variant", "Unsupported callout variant");
                }

                inlines(callout.inlines(), path + ".inlines");
            } else if (block instanceof Quote quote) {
                inlines(quote.inlines(), path + ".inlines");
            }
        }
    }

    private void table(Table table, String path) {
        array(table.headers(), 0, 20, path + ".headers");
        array(table.rows(), 1, 100, path + ".rows");
        array(table.rows().get(0), 1, 20, path + ".rows[0]");

        int columns = table.rows().get(0).size();

        if (!table.headers().isEmpty() && table.headers().size() != columns) {
            throw invalid(path + ".headers", "Header count must match column count");
        }

        for (var cell : table.headers()) {
            inlines(cell, path + ".headers");
        }

        for (var row : table.rows()) {
            array(row, columns, columns, path + ".rows");

            for (var cell : row) {
                inlines(cell, path + ".rows");
            }
        }
    }

    private void inlines(List<Inline> inlines, String path) {
        array(inlines, 1, 100, path);
        for (var inline : inlines) {
            plainText(inline.text(), path + ".text", false);
            array(inline.marks(), 0, 3, path + ".marks");

            if (!MARKS.containsAll(inline.marks())
                    || new HashSet<>(inline.marks()).size() != inline.marks().size()
            ) {
                throw invalid(
                        path + ".marks",
                        "Marks must be unique bold, italic or underline values"
                );
            }
        }
    }

    private void text(String text, String path) {
        plainText(text, path, true);
    }

    private void plainText(String text, String path, boolean requireNonblank) {
        if (text == null
                || text.isEmpty()
                || (requireNonblank && text.isBlank())
                || text.length() > MAX_TEXT_LENGTH
                || text.indexOf('<') >= 0
                || text.indexOf('>') >= 0
                || text.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')
        ) {
            throw invalid(
                    path,
                    "Expected nonblank plain text of at most 10000 UTF-16 code units without markup or control characters"
            );
        }
    }

    private void array(List<?> items, int minimum, int maximum, String path) {
        if (items == null
                || items.size() < minimum
                || items.size() > maximum
                || items.stream().anyMatch(java.util.Objects::isNull)
        ) {
            throw invalid(path, "Array length or element is invalid");
        }
    }

    public static ValidationException invalid(String path, String message) {
        StructuredLog.event(
                LOG,
                Logger.Level.DEBUG,
                "lesson.content.validation.rejected",
                "Grammar document rejected because its structure does not meet the schema requirements",
                Map.of("reason", "invalid_structure")
        );

        return new ValidationException(
                "LESSON_CONTENT_INVALID",
                "Invalid lesson content",
                List.of(new ValidationError(path, message))
        );
    }

    public static ValidationException unsupported() {
        StructuredLog.event(
                LOG,
                Logger.Level.DEBUG,
                "lesson.content.validation.rejected",
                "Grammar document rejected because its schema version is unsupported",
                java.util.Map.of("reason", "unsupported_schema")
        );

        return new ValidationException(
                "LESSON_CONTENT_SCHEMA_UNSUPPORTED",
                "Only lesson content schema version 1 is supported", List.of()
        );
    }
}
