package com.japaneselearning.quiz.domain;

// The fingerprint includes the source's updated_at, so reverting an edit does not revalidate it.
public record ExampleReading(
        String reading,
        String fingerprint
) {
}
