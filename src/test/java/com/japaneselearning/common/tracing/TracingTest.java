package com.japaneselearning.common.tracing;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TracingTest {
    @Test
    void crossStackRatioVectorsAndFixedWindowBudgetAgree() {
        var sampler = new BoundarySampler(0.1, 1, () -> 42L);
        String low = "00000000000000010000000000000001";

        assertEquals(SamplingDecision.DROP, sampler.shouldSample(
                Context.root(), TRACE, "server", SpanKind.SERVER, Attributes.empty(), List.of()
        ).getDecision());
        assertEquals(SamplingDecision.RECORD_AND_SAMPLE, sampler.shouldSample(
                Context.root(), low, "server", SpanKind.SERVER, Attributes.empty(), List.of()
        ).getDecision());
        assertEquals(SamplingDecision.DROP, sampler.shouldSample(
                Context.root(), low, "server", SpanKind.SERVER, Attributes.empty(), List.of()
        ).getDecision());
    }

    private static final String TRACE = "abcdef0123456789abcdef0123456789";
    private static final String PARENT = "0123456789abcdef";
    private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier.get(key);
        }
    };

    @Test
    void preservesW3cLineageWhileOmittingBaggageAndVendorState() {
        var propagation = new W3cOnly();
        var headers = Map.of(
                "traceparent", "00-" + TRACE + "-" + PARENT + "-01",
                "tracestate", "vendor=privacy-sentinel",
                "baggage", "email=privacy-sentinel"
        );

        Context parent = propagation.extract(Context.root(), headers, GETTER);

        var injected = new HashMap<String, String>();

        propagation.inject(parent, injected, Map::put);

        assertEquals(1, injected.size());
        assertEquals(headers.get("traceparent"), injected.get("traceparent"));
        assertEquals(TRACE, Span.fromContext(parent).getSpanContext().getTraceId());
    }

    @Test
    void rejectsMalformedZeroAndCombinedDuplicateParents() {
        var propagation = new W3cOnly();

        for (String value : new String[]{
                "invalid",
                "00-00000000000000000000000000000000-" + PARENT + "-01",
                "00-" + TRACE + "-0000000000000000-01",
                "00-" + TRACE + "-" + PARENT + "-01,duplicate"
        }) {
            Context extracted = propagation.extract(
                    Context.root(), Map.of("traceparent", value), GETTER
            );

            assertFalse(Span.fromContext(extracted).getSpanContext().isValid());
        }
    }

    @Test
    void remoteSampledCannotForceRecordingAndLocalChildrenFollowParent() {
        SpanContext remote = SpanContext.createFromRemoteParent(
                TRACE, PARENT, TraceFlags.getSampled(), TraceState.getDefault()
        );
        Context parent = Context.root().with(Span.wrap(remote));

        try (var provider = SdkTracerProvider.builder()
                .setSampler(TraceConfiguration.boundarySampler(0))
                .build()
        ) {
            Span span = provider.get("p06-test")
                    .spanBuilder("server")
                    .setParent(parent)
                    .startSpan();

            assertFalse(span.isRecording());
            assertEquals(TRACE, span.getSpanContext().getTraceId());
            span.end();

            SpanContext local = SpanContext.create(
                    TRACE, PARENT, TraceFlags.getSampled(), TraceState.getDefault()
            );
            Span child = provider.get("p06-test")
                    .spanBuilder("client")
                    .setParent(Context.root().with(Span.wrap(local)))
                    .startSpan();

            assertTrue(child.isRecording());
            child.end();
        }
    }

    @Test
    void exportedDatabaseSnapshotKeepsIdsAndStatusAndDropsSensitiveContent() {
        try (var provider = SdkTracerProvider.builder().build()) {
            Span span = provider.get("p06-test")
                    .spanBuilder("SELECT privacy-sentinel")
                    .setSpanKind(SpanKind.CLIENT)
                    .startSpan();

            span.setAttribute("db.system", "mysql");
            span.setAttribute("db.statement", "privacy-sentinel");
            span.setAttribute("db.query.parameter.email", "privacy-sentinel");
            span.setAttribute("url.full", "https://privacy-sentinel");
            span.setAttribute("enduser.id", "privacy-sentinel");
            span.setStatus(StatusCode.ERROR, "privacy-sentinel");
            span.recordException(new IllegalArgumentException("privacy-sentinel"));
            span.end();

            var original = ((ReadableSpan) span).toSpanData();
            var safe = TraceConfiguration.sanitize(original);

            assertEquals(original.getTraceId(), safe.getTraceId());
            assertEquals(original.getSpanId(), safe.getSpanId());
            assertEquals("database.command", safe.getName());
            assertEquals(StatusCode.ERROR, safe.getStatus().getStatusCode());
            assertEquals("", safe.getStatus().getDescription());
            assertTrue(safe.getEvents().isEmpty());
            assertEquals(
                    "mysql",
                    safe.getAttributes().get(AttributeKey.stringKey("db.system.name"))
            );
            assertFalse(safe.getAttributes().toString().contains("privacy-sentinel"));
        }
    }
}
