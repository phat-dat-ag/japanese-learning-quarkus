package com.japaneselearning.common.web;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.jboss.logging.Logger;
import org.jboss.logmanager.MDC;

import java.time.Duration;

import org.eclipse.microprofile.openapi.annotations.Operation;

// Test-only endpoint verifies context after a non-blocking asynchronous boundary.
@Path("/correlation-probe")
@PermitAll
public class CorrelationProbeResource {
    @GET
    @Operation(hidden = true)
    public Uni<String> probe() {
        return Uni.createFrom().item("probe").onItem().delayIt().by(Duration.ofMillis(10))
                .map(ignored -> {
                    Logger.getLogger(CorrelationProbeResource.class).info("Correlation probe application log");
                    return String.valueOf(MDC.get("correlationId"));
                });
    }
}
