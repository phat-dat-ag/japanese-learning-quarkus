package com.japaneselearning.vocabulary.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.japaneselearning.vocabulary.dto.LessonWriteRequest;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class LessonBatchFileReader {
    private static final Logger LOG = Logger.getLogger(LessonBatchFileReader.class);
    public static final int MAX_FILE_BYTES = 1024 * 1024;

    private final Vertx vertx;
    private final ObjectReader reader;

    public LessonBatchFileReader(Vertx vertx, ObjectMapper mapper) {
        this.vertx = vertx;
        this.reader = mapper.readerFor(new TypeReference<List<LessonWriteRequest>>() {
                })
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .without(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
    }

    public Uni<List<LessonWriteRequest>> read(Path file) {
        return vertx.fileSystem().props(file.toString()).flatMap(properties -> {
            if (properties.size() > MAX_FILE_BYTES) {
                LOG.warnf("Lesson batch rejected reason=file_size bytes=%d limit=%d", properties.size(), MAX_FILE_BYTES);
                return Uni.createFrom().failure(new WebApplicationException(413));
            }

            if (properties.size() == 0) {
                LOG.debug("Lesson batch rejected reason=empty_file");
                return Uni.createFrom().failure(new BadRequestException());
            }

            return vertx.fileSystem().readFile(file.toString())
                    .map(buffer -> parse(buffer.getBytes()));
        });
    }

    private List<LessonWriteRequest> parse(byte[] content) {
        try {
            List<LessonWriteRequest> requests = reader.readValue(content);

            if (requests == null
                    || requests.isEmpty()
                    || requests.size() > LessonBatchService.MAX_BATCH_SIZE
            ) {
                throw new BadRequestException();
            }

            return requests;
        } catch (IOException exception) {
            LOG.debug("Lesson batch rejected reason=invalid_json");
            throw new BadRequestException();
        }
    }
}
