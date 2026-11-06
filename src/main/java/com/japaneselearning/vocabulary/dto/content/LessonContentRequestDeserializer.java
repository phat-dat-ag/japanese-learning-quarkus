package com.japaneselearning.vocabulary.dto.content;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.japaneselearning.vocabulary.service.LessonContentValidator;
import jakarta.ws.rs.WebApplicationException;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;

/**
 * Limits and strict typing apply only to this request, never to the global Jackson mapper.
 */
public class LessonContentRequestDeserializer extends JsonDeserializer<LessonContentWriteRequest> {
    @Override
    public LessonContentWriteRequest deserialize(
            JsonParser parser,
            DeserializationContext context
    ) throws IOException {
        try (TokenBuffer buffer = new TokenBuffer(parser)) {
            int depth = 0;
            int tokens = 0;

            do {
                JsonToken token = parser.currentToken();

                if (++tokens > 100_000) {
                    throw LessonContentValidator.invalid(
                            "content",
                            "Document has too many elements"
                    );
                }

                if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                    if (++depth > LessonContentValidator.MAX_DEPTH) {
                        throw LessonContentValidator.invalid(
                                "content",
                                "Document nesting exceeds the limit"
                        );
                    }
                } else if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
                    depth--;
                }

                long offset = Math.max(
                        parser.currentLocation().getByteOffset(),
                        parser.currentLocation().getCharOffset()
                );

                if (offset > LessonContentValidator.MAX_DOCUMENT_BYTES
                        || (token == JsonToken.VALUE_STRING
                        && parser.getTextLength() > LessonContentValidator.MAX_DOCUMENT_BYTES)
                ) {
                    throw new WebApplicationException(413);
                }

                buffer.copyCurrentEvent(parser);

                if (depth == 0) {
                    break;
                }
            } while (parser.nextToken() != null);

            if (parser.nextToken() != null) {
                throw LessonContentValidator.invalid(
                        "request",
                        "Trailing JSON values are not permitted"
                );
            }

            try (JsonParser bounded = buffer.asParser()) {
                JsonNode tree = LessonContentValidator.json().readTree(bounded);

                if (tree == null || !tree.isObject()) {
                    throw LessonContentValidator.invalid("request", "Expected an object");
                }

                var names = tree.fieldNames();

                while (names.hasNext()) {
                    if (!java.util.Set.of(
                            "schemaVersion", "expectedVersion", "content"
                    ).contains(names.next())) {
                        throw LessonContentValidator.invalid("request", "Unknown field");
                    }
                }

                JsonNode schema = tree.get("schemaVersion");
                JsonNode revision = tree.get("expectedVersion");

                if (schema == null
                        || !schema.isIntegralNumber()
                        || !schema.canConvertToInt()
                        || revision == null
                        || !revision.isIntegralNumber()
                        || !revision.canConvertToLong()
                ) {
                    throw LessonContentValidator.invalid(
                            "request",
                            "Integer schemaVersion and expectedVersion are required"
                    );
                }

                if (schema.intValue() != 1) {
                    throw LessonContentValidator.unsupported();
                }

                return new LessonContentWriteRequest(
                        schema.intValue(),
                        revision.longValue(),
                        LessonContentValidator.json().treeToValue(
                                tree.get("content"),
                                LessonContentDocument.class
                        )
                );
            } catch (JsonProcessingException failure) {
                throw LessonContentValidator.invalid("content", "Document does not match schema v1");
            }
        }
    }
}
