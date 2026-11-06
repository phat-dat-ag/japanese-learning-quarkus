package com.japaneselearning.vocabulary.service;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.japaneselearning.common.logging.LogOperation;
import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.common.exception.ValidationError;
import com.japaneselearning.vocabulary.dto.content.LessonContentImportItem;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Validator;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class LessonContentImportReader {
    private final Vertx vertx;
    private final Validator beanValidator;
    private final LessonContentValidator contentValidator;
    private final long maxBytes;
    private final int maxItems;
    private final ObjectMapper json;

    public LessonContentImportReader(
            Vertx vertx,
            Validator beanValidator,
            LessonContentValidator contentValidator,
            @ConfigProperty(
                    name = "lesson.content.import.max-file-bytes",
                    defaultValue = "5242880"
            )
            long maxBytes,

            @ConfigProperty(
                    name = "lesson.content.import.max-items",
                    defaultValue = "100"
            )
            int maxItems
    ) {
        this.vertx = vertx;
        this.beanValidator = beanValidator;
        this.contentValidator = contentValidator;

        if (maxBytes < 1
                || maxBytes > 10 * 1024 * 1024
                || maxItems < 1
                || maxItems > 500
        ) {
            throw new IllegalArgumentException(
                    "Lesson content import limits must be within documented bounds"
            );
        }

        this.maxBytes = maxBytes;
        this.maxItems = maxItems;

        json = LessonContentValidator.json().copy();
        json.getFactory()
                .setStreamReadConstraints(StreamReadConstraints
                        .builder()
                        .maxNestingDepth(LessonContentValidator.MAX_DEPTH)
                        .maxStringLength(LessonContentValidator.MAX_DOCUMENT_BYTES)
                        .build()
                );
    }

    @LogOperation(
            value = "lesson.content.import.read",
            description = "Grammar lesson import file validation",
            level = Logger.Level.DEBUG
    )
    public Uni<List<LessonContentImportItem>> read(Path file) {
        return vertx.fileSystem()
                .props(file.toString())
                .invoke(properties -> checkSize(properties.size()))
                .chain(() -> vertx.fileSystem().readFile(file.toString()))
                .chain(buffer -> {
                    checkSize(buffer.length());

                    return vertx.executeBlocking(() -> parse(buffer.getBytes()));
                });
    }

    List<LessonContentImportItem> parse(byte[] bytes) {
        checkSize(bytes.length);
        try {
            var items = json.readValue(
                    bytes,
                    new TypeReference<List<LessonContentImportItem>>() {
                    }
            );

            if (items == null
                    || items.isEmpty()
                    || items.size() > maxItems
            ) {
                throw LessonContentValidator.invalid(
                        "file",
                        "File item count exceeds the configured limits"
                );
            }

            for (int i = 0; i < items.size(); i++) {
                var item = items.get(i);

                if (item == null || !beanValidator.validate(item).isEmpty()) {
                    throw LessonContentValidator.invalid(
                            "[" + i + "]",
                            "Invalid lesson metadata or content envelope"
                    );
                }

                try {
                    contentValidator.validate(item.document());
                } catch (ValidationException failure) {
                    String prefix = "[" + i + "].";

                    throw new ValidationException(
                            failure.getCode(),
                            "Invalid grammar content at import item " + i,
                            failure.getErrors().stream().map(error -> new ValidationError(
                                    prefix + error.field(), error.message()
                            )).toList()
                    );
                }
            }

            return List.copyOf(items);
        } catch (IOException failure) {
            throw LessonContentValidator.invalid(
                    "file",
                    "Expected a strict JSON array of grammar lessons matching schema v1"
            );
        }
    }

    private void checkSize(long size) {
        if (size > maxBytes) {
            throw new WebApplicationException(413);
        }

        if (size == 0) {
            throw LessonContentValidator.invalid(
                    "file",
                    "File must not be empty"
            );
        }
    }
}
