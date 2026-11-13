package com.japaneselearning.common.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.vertx.core.spi.observability.HttpRequest;

import java.util.Collection;
import java.util.List;

public final class W3cOnly implements TextMapPropagator {
    private final TextMapPropagator delegate = W3CTraceContextPropagator.getInstance();

    @Override
    public Collection<String> fields() {
        return List.of("traceparent");
    }

    @Override
    public <C> void inject(Context context, C carrier, TextMapSetter<C> setter) {
        delegate.inject(context, carrier, (target, key, value) -> {
            if ("traceparent".equals(key)) {
                setter.set(target, key, value);
            }
        });
    }

    @Override
    public <C> Context extract(Context context, C carrier, TextMapGetter<C> getter) {
        if (carrier instanceof HttpRequest request
                && request.headers().getAll("traceparent").size() != 1
        ) {
            return context;
        }

        TextMapGetter<C> safe = new TextMapGetter<>() {
            @Override
            public Iterable<String> keys(C source) {
                return List.of("traceparent");
            }

            @Override
            public String get(C source, String key) {
                return "traceparent".equals(key) ? getter.get(source, key) : null;
            }
        };

        Context extracted = delegate.extract(context, carrier, safe);
        SpanContext span = Span.fromContext(extracted).getSpanContext();

        if (!span.isValid()) {
            return context;
        }

        return context.with(Span.wrap(SpanContext.createFromRemoteParent(
                span.getTraceId(),
                span.getSpanId(),
                span.getTraceFlags(),
                TraceState.getDefault()
        )));
    }
}
