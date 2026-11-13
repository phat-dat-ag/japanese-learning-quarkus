package com.japaneselearning.common.tracing;

import io.opentelemetry.context.propagation.TextMapPropagator;
import io.quarkus.opentelemetry.runtime.propagation.TextMapPropagatorCustomizer;

import jakarta.inject.Singleton;

@Singleton
public class TracePropagation implements TextMapPropagatorCustomizer {
    @Override
    public TextMapPropagator customize(TextMapPropagatorCustomizer.Context ignored) {
        return new W3cOnly();
    }
}
