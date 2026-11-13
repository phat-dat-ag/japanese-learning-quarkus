package com.japaneselearning.common.tracing;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.DelegatingSpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.quarkus.opentelemetry.runtime.AutoConfiguredOpenTelemetrySdkBuilderCustomizer;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.List;

@Singleton
public class TraceConfiguration
        implements AutoConfiguredOpenTelemetrySdkBuilderCustomizer {

    @ConfigProperty(name = "quarkus.otel.traces.sampler.arg")
    double ratio;

    @ConfigProperty(name = "application.tracing.max-roots-per-second", defaultValue = "10")
    int maxRootsPerSecond;

    @Override
    public void customize(AutoConfiguredOpenTelemetrySdkBuilder builder) {
        builder.addSamplerCustomizer((previous, properties) ->
                new BoundarySampler(ratio, maxRootsPerSecond)
        );
        builder.addSpanExporterCustomizer((
                exporter,
                properties
        ) -> new SafeExporter(exporter));
    }

    public static Sampler boundarySampler(double probability) {
        return new BoundarySampler(probability, 10);
    }

    public static SpanData sanitize(SpanData original) {
        Attributes attributes = original.getAttributes();
        String method = attributes.get(AttributeKey.stringKey("http.request.method"));

        if (method == null) {
            method = attributes.get(AttributeKey.stringKey("http.method"));
        }

        String safeMethod = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
                .contains(method == null ? "" : method) ? method : "OTHER";
        Long status = attributes.get(AttributeKey.longKey("http.response.status_code"));

        if (status == null) {
            status = attributes.get(AttributeKey.longKey("http.status_code"));
        }

        boolean database = attributes.get(AttributeKey.stringKey("db.system")) != null
                || attributes.get(AttributeKey.stringKey("db.system.name")) != null;
        String route = attributes.get(AttributeKey.stringKey("http.route"));
        String safeRoute = "/unmatched";

        if (route != null
                && route.length() <= 160
                && route.startsWith("/api/")
                && route.matches("[A-Za-z0-9_/{}/-]+")
                && (route.contains("{") || List.of(
                "/api/v1/lessons",
                "/api/v1/flashcards",
                "/api/v1/jlpt-levels",
                "/api/vocabularies/import"
        ).contains(route))
        ) {
            safeRoute = route;
        }

        var safe = Attributes.builder();

        if (database) {
            safe.put("db.system.name", "mysql");
        } else {
            safe.put("http.request.method", safeMethod);

            if (original.getKind() == SpanKind.SERVER) {
                safe.put("http.route", safeRoute);
            }

            if (status != null && status >= 100 && status <= 599) {
                safe.put("http.response.status_code", status);
            }
        }

        if (original.getStatus().getStatusCode()
                == StatusCode.ERROR
        ) {
            safe.put("error.type", database ? "database" : "transport_or_http");
        }

        Attributes filtered = safe.build();
        var resources = Attributes.builder();

        for (String key : List.of(
                "service.name",
                "service.namespace",
                "service.version",
                "service.instance.id",
                "deployment.environment.name"
        )) {
            String value = original.getResource()
                    .getAttribute(AttributeKey.stringKey(key));

            if (value != null && value.matches("[A-Za-z0-9_.-]{1,80}")) {
                resources.put(key, value);
            }
        }

        Resource resource = Resource.create(resources.build());

        return new DelegatingSpanData(original) {
            @Override
            public Resource getResource() {
                return resource;
            }

            @Override
            public String getName() {
                return database ? "database.command" : "HTTP " + original.getKind().name();
            }

            @Override
            public Attributes getAttributes() {
                return filtered;
            }

            @Override
            public StatusData getStatus() {
                return StatusData.create(original.getStatus().getStatusCode(), "");
            }

            @Override
            public List<EventData> getEvents() {
                return List.of();
            }

            @Override
            public List<LinkData> getLinks() {
                return List.of();
            }

            @Override
            public int getTotalRecordedEvents() {
                return 0;
            }

            @Override
            public int getTotalRecordedLinks() {
                return 0;
            }

            @Override
            public int getTotalAttributeCount() {
                return filtered.size();
            }

            @Override
            public SpanContext getSpanContext() {
                SpanContext context = original.getSpanContext();

                return SpanContext.create(
                        context.getTraceId(),
                        context.getSpanId(),
                        context.getTraceFlags(),
                        TraceState.getDefault()
                );
            }
        };
    }

    static class SafeExporter implements SpanExporter {
        private final SpanExporter delegate;

        SafeExporter(SpanExporter delegate) {
            this.delegate = delegate;
        }

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            List<SpanData> sanitized = spans.stream()
                    .map(TraceConfiguration::sanitize)
                    .toList();

            return delegate.export(sanitized);
        }

        @Override
        public CompletableResultCode flush() {
            return delegate.flush();
        }

        @Override
        public CompletableResultCode shutdown() {
            return delegate.shutdown();
        }
    }
}
