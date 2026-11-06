package com.japaneselearning.vocabulary.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.orm.JsonFormat;
import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.FormatMapper;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;

@ApplicationScoped
@JsonFormat
@PersistenceUnitExtension
public class LessonContentJsonFormat implements FormatMapper {
    // Persistence JSON must not inherit REST null omission or generated DTO serializers.
    private final FormatMapper delegate = new JacksonJsonFormatMapper(new ObjectMapper());

    @Override
    public <T> T fromString(CharSequence value, JavaType<T> type, WrapperOptions options) {
        return delegate.fromString(value, type, options);
    }

    @Override
    public <T> String toString(T value, JavaType<T> type, WrapperOptions options) {
        return delegate.toString(value, type, options);
    }
}
