package com.japaneselearning.security;

import java.util.HashMap;
import java.util.Map;

public class QuizImportMysqlTestProfile extends VocabularyMysqlTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>(super.getConfigOverrides());

        config.put("quiz.import.max-file-bytes", "4096");
        config.put("quiz.import.max-items", "3");

        return config;
    }
}
