package com.japaneselearning.security;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class VocabularyMysqlTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        String url = System.getProperty("vocabulary.mysql.url");
        if (url == null || !url.endsWith("/vocabulary_import_test")) {
            throw new IllegalArgumentException("MySQL tests require a dedicated vocabulary_import_test database");
        }
        return Map.of(
                "quarkus.datasource.reactive.url", url,
                "quarkus.datasource.username", "root",
                "quarkus.datasource.password", System.getProperty("vocabulary.mysql.password", "vocabulary-test"),
                "quarkus.datasource.devservices.enabled", "false",
                "quarkus.hibernate-orm.database.start-offline", "false");
    }
}
