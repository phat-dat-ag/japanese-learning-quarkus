package com.japaneselearning.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.japaneselearning.vocabulary.entity.Lesson;
import com.japaneselearning.vocabulary.entity.LessonContent;
import com.japaneselearning.vocabulary.repository.LessonContentRepository;
import com.japaneselearning.vocabulary.repository.LessonRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.vertx.VertxContextSupport;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(VocabularyMysqlTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
@EnabledIfSystemProperty(named = "vocabulary.mysql.tests", matches = "true")
class LessonContentMappingMysqlTest {
    @Inject
    LessonContentRepository contents;
    @Inject
    LessonRepository lessons;
    @Inject
    ObjectMapper json;

    @Test
    void nativeJsonInsertReadAndReplacementPreserveJapanese() throws Throwable {
        var original = json.readTree("{\"blocks\":[{\"text\":\"私は学生です。𠮷\"}]}");
        var replacement = json.readTree("{\"blocks\":[{\"text\":\"日本語の更新\"}]}");

        Long id = VertxContextSupport.subscribeAndAwait(() -> Panache.withTransaction(() -> {
            Lesson lesson = new Lesson();

            lesson.levelId = 1L;
            lesson.lessonNumber = ThreadLocalRandom.current().nextInt(1000000, 1000000000);
            lesson.displayOrder = lesson.lessonNumber;
            lesson.title = "JSON mapping fixture";
            lesson.createdAt = lesson.updatedAt = LocalDateTime.now(ZoneOffset.UTC);

            return lessons.persist(lesson).chain(() -> {
                LessonContent content = new LessonContent();

                content.lessonId = lesson.id;
                content.schemaVersion = 1;
                content.version = 1;
                content.content = original;
                content.createdAt = content.updatedAt = LocalDateTime.now(ZoneOffset.UTC);

                return contents.persistAndFlush(content).replaceWith(lesson.id);
            });
        }));

        VertxContextSupport.subscribeAndAwait(() -> Panache.withTransaction(() ->
                contents.findById(id).invoke(content -> {
                    assertEquals(original, content.content);
                    content.content = replacement;
                    content.version++;
                }).call(contents::flush)
        ));

        VertxContextSupport.subscribeAndAwait(() -> Panache.withTransaction(() ->
                contents.findById(id).invoke(content -> {
                    assertEquals(replacement, content.content);
                    assertEquals(2, content.version);
                }).chain(() -> lessons.deleteById(id))
        ));
    }
}
