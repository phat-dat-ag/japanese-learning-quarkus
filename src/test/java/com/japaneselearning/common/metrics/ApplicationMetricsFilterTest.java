package com.japaneselearning.common.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilterReply;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationMetricsFilterTest {
    @Test
    void collapsesArbitraryMethodsPathsAndExtraLabelsToOneSeries() {
        var filter = new ApplicationMetricsFilter();
        var results = new HashSet<Meter.Id>();
        for (int i = 0; i < 100; i++) {
            var id = new Meter.Id("http.server.requests", Tags.of("method", "SECRET" + i,
                    "uri", "/unknown/" + i, "status", "401", "user_id", "secret" + i),
                    "seconds", "HTTP", Meter.Type.TIMER);
            results.add(filter.map(id));
        }
        assertEquals(1, results.size());
        assertEquals(3, results.iterator().next().getTags().size());
        var route = new Meter.Id("http.server.requests", Tags.of("method", "GET",
                "uri", "/api/v1/flashcards/{id}", "status", "200"), null, null, Meter.Type.TIMER);
        assertEquals("flashcards", filter.map(route).getTag("route"));
        assertEquals(filter.map(route), filter.map(filter.map(route)));
        var client = new Meter.Id("http.client.requests", Tags.of("uri", "sensitive"), null, null, Meter.Type.TIMER);
        assertEquals(MeterFilterReply.DENY, filter.accept(client));
    }
}
