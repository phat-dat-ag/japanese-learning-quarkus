package com.japaneselearning.quiz.admin.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.japaneselearning.quiz.admin.dto.QuizImportError;
import com.japaneselearning.quiz.admin.dto.QuizQuestionCreateRequest;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Validator;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class QuizImportFileReader {
    private final Vertx vertx;
    private final ObjectReader reader;
    private final Validator validator;
    private final long maxFileBytes;
    private final int maxItems;

    public QuizImportFileReader(
            Vertx vertx,
            ObjectMapper mapper,
            Validator validator,
            @ConfigProperty(name = "quiz.import.max-file-bytes", defaultValue = "1048576")
            long maxFileBytes,

            @ConfigProperty(name = "quiz.import.max-items", defaultValue = "100")
            int maxItems
    ) {
        if (
                maxFileBytes < 1
                        || maxFileBytes > Integer.MAX_VALUE
                        || maxItems < 1
        ) {
            throw new IllegalArgumentException(
                    "Quiz import limits must be positive; file bytes must fit int32"
            );
        }

        this.vertx = vertx;
        this.validator = validator;
        this.maxFileBytes = maxFileBytes;
        this.maxItems = maxItems;
        this.reader = mapper.copy()
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .readerFor(QuizQuestionCreateRequest.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .without(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public Uni<List<QuizImportItem>> read(Path file, String contentType) {
        String mediaType = contentType == null
                ? "application/octet-stream"
                : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);

        if (!List.of("application/json", "application/octet-stream").contains(mediaType)) {
            throw QuizImportErrors.invalid(
                    "file",
                    "File content type must be application/json or application/octet-stream"
            );
        }

        return vertx.fileSystem().props(file.toString()).chain(properties -> {
            checkSize(properties.size());

            return vertx.fileSystem().readFile(file.toString());
        }).chain(buffer -> {
            checkSize(buffer.length());
            // Parsing and Bean Validation run on a worker; completion restores the caller's Vert.x context.
            return vertx.executeBlocking(() -> parse(buffer.getBytes()));
        });
    }

    private void checkSize(long bytes) {
        if (bytes > maxFileBytes) {
            throw new WebApplicationException(413);
        }

        if (bytes == 0) {
            throw QuizImportErrors.invalid(
                    "file",
                    "File must contain a nonempty JSON array"
            );
        }
    }

    private List<QuizImportItem> parse(byte[] content) {
        int index = -1;
        List<QuizImportItem> requests = new ArrayList<>();

        try (JsonParser parser = reader.createParser(content)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw QuizImportErrors.invalid(
                        "file",
                        "File must contain a JSON array of question creation objects"
                );
            }

            while (true) {
                index = requests.size();
                if (parser.nextToken() == JsonToken.END_ARRAY) {
                    break;
                }

                if (parser.currentToken() == null) {
                    throw QuizImportErrors.invalid(
                            "[" + index + "]", "Incomplete JSON array"
                    );
                }

                if (index >= maxItems) {
                    throw QuizImportErrors.invalid(
                            "file",
                            "Import must contain at most " + maxItems + " questions"
                    );
                }

                requests.add(prepare(index, reader.readTree(parser)));
            }

            index = -1;

            if (parser.nextToken() != null) {
                throw QuizImportErrors.invalid(
                        "file",
                        "Trailing JSON content is not allowed"
                );
            }
        } catch (IOException failure) {
            throw QuizImportErrors.invalid(
                    index < 0 ? "file" : "[" + index + "]", "Malformed JSON"
            );
        }

        if (requests.isEmpty()) {
            throw QuizImportErrors.invalid("file", "Import must contain at least one question");
        }
        return List.copyOf(requests);
    }

    private QuizImportItem prepare(int index, JsonNode node) throws IOException {
        String prefix = "[" + index + "]";
        if (node == null || !node.isObject()) {
            return new QuizImportItem(null, List.of(new QuizImportError(
                    prefix, "QUIZ_ITEM_INVALID", "Question must be a nonnull JSON object"
            )));
        }
        QuizQuestionCreateRequest request;
        try {
            request = reader.readValue(node);
        } catch (JsonMappingException failure) {
            return new QuizImportItem(null, List.of(new QuizImportError(
                    jsonPath(index, failure), "QUIZ_ITEM_JSON_INVALID", "Invalid question structure or field type"
            )));
        }
        List<QuizImportError> errors = validator.validate(request).stream()
                .map(violation -> new QuizImportError(
                        prefix + "." + violation.getPropertyPath().toString().replace(".<list element>", ""),
                        "QUIZ_ITEM_INVALID", violation.getMessage()
                ))
                .sorted(
                        Comparator.comparing(QuizImportError::field)
                                .thenComparing(QuizImportError::message)
                )
                .toList();
        return new QuizImportItem(request, errors);
    }

    private String jsonPath(int index, JsonMappingException failure) {
        StringBuilder path = new StringBuilder(index < 0 ? "file" : "[" + index + "]");

        failure.getPath().forEach(reference -> {
            if (reference.getFieldName() != null) {
                path.append('.').append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        });

        return path.toString();
    }
}
