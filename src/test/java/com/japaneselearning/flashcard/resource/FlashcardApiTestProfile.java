package com.japaneselearning.flashcard.resource;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;
import java.util.Set;

public class FlashcardApiTestProfile implements QuarkusTestProfile {

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(FlashcardApiTestRepository.class);
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.datasource.devservices.enabled", "false",
                "quarkus.hibernate-orm.database.start-offline", "true",
                "quarkus.datasource.username", "test",
                "quarkus.datasource.password", "test",
                "quarkus.datasource.reactive.url", "mysql://localhost:3306/unused"
        );
    }
}
