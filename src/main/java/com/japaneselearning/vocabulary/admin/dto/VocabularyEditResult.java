package com.japaneselearning.vocabulary.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "EditResult")
public record VocabularyEditResult(@JsonProperty("id") Long editedResourceId) {
}
