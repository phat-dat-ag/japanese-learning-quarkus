package com.japaneselearning.common.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdValidationTest {
    @Test
    void validatesBoundariesAndRejectsUnsafeAndDuplicateValues() {
        for (String value : List.of("a", "safe_ID-123", "a".repeat(64))) {
            assertEquals(value, CorrelationIdFilter.getOrGenerateId(List.of(value)));
        }
        for (var values : List.of(List.<String>of(), List.of(""), List.of("a".repeat(65)),
                List.of("unsafe value"), List.of("bad\r\nvalue"), List.of("bad\tvalue"),
                List.of("?"), List.of("a,b"), List.of("first", "second"))) {
            assertTrue(CorrelationIdFilter.getOrGenerateId(values).matches("[a-f0-9]{32}"));
        }
        assertNotEquals(CorrelationIdFilter.getOrGenerateId(List.of()), CorrelationIdFilter.getOrGenerateId(List.of()));
    }
}
