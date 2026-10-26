package com.japaneselearning.common.web;

import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.openapi.annotations.Operation;

@Path("/trace-client-probe")
@PermitAll
public class TraceClientProbeResource {
    private final Vertx vertx;

    public TraceClientProbeResource(Vertx vertx) {
        this.vertx = vertx;
    }

    @GET
    @Operation(hidden = true)
    public Uni<String> call(@QueryParam("port") int port) {
        var client = vertx.createHttpClient();

        return Uni.createFrom().completionStage(
                        client.request(HttpMethod.GET, port, "127.0.0.1", "/")
                                .compose(request -> request.send())
                                .map(response -> "ok").toCompletionStage()
                )
                .eventually(() -> Uni.createFrom().completionStage(client.close().toCompletionStage()));
    }
}
