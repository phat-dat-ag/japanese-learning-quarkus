package com.japaneselearning.common.tracing;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;

import java.util.List;
import java.util.function.LongSupplier;

public final class BoundarySampler implements Sampler {
    private final double probability;
    private final int maxRootsPerSecond;
    private final LongSupplier clock;
    private long window;
    private int used;

    public BoundarySampler(double probability, int maxRootsPerSecond) {
        this(probability, maxRootsPerSecond, () -> System.nanoTime() / 1_000_000_000L);
    }

    BoundarySampler(double probability, int maxRootsPerSecond, LongSupplier clock) {
        if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
            throw new IllegalArgumentException("Trace probability must be between zero and one");
        }

        if (maxRootsPerSecond < 1 || maxRootsPerSecond > 1000) {
            throw new IllegalArgumentException("Trace root budget must be between 1 and 1000");
        }

        this.probability = probability;
        this.maxRootsPerSecond = maxRootsPerSecond;
        this.clock = clock;
    }

    @Override
    public SamplingResult shouldSample(
            Context parentContext,
            String traceId,
            String name,
            SpanKind kind,
            Attributes attributes,
            List<LinkData> links
    ) {
        var parent = Span.fromContext(parentContext).getSpanContext();

        if (parent.isValid() && !parent.isSampled()) {
            return SamplingResult.drop();
        }

        if (parent.isValid() && !parent.isRemote()) {
            return SamplingResult.recordAndSample();
        }

        long value = Long.parseUnsignedLong(traceId.substring(0, 16), 16) >>> 1;

        if (probability <= 0 || probability < 1 && value >= probability * Long.MAX_VALUE) {
            return SamplingResult.drop();
        }

        return takeBudget() ? SamplingResult.recordAndSample() : SamplingResult.drop();
    }

    private synchronized boolean takeBudget() {
        long current = clock.getAsLong();

        if (current != window) {
            window = current;
            used = 0;
        }

        return used++ < maxRootsPerSecond;
    }

    @Override
    public String getDescription() {
        return "ParentBasedWithPublicBoundaryBudget";
    }
}
