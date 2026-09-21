package com.japaneselearning.common.web;

import com.japaneselearning.common.exception.ConflictException;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;

@Path("/error-probe")
@PermitAll
public class ErrorProbeResource {
    @GET
    @Path("/{kind}")
    @Operation(hidden = true)
    public Uni<Response> error(@PathParam("kind") String kind) {
        RuntimeException failure = switch (kind) {
            case "missing" -> new NotFoundException("credential-sentinel");
            case "bad" -> new BadRequestException("credential-sentinel SQL SELECT private_table");
            case "conflict" -> new ConflictException("TEST_CONFLICT", "Already exists");
            case "method" ->
                    new WebApplicationException("credential-sentinel", Response.status(405).allow("GET").build());
            case "busy" ->
                    new WebApplicationException("credential-sentinel", Response.status(503).header("Retry-After", "5").build());
            default ->
                    new IllegalStateException("credential-sentinel password=private", new RuntimeException("SQL SELECT private_table"));
        };
        return Uni.createFrom().failure(failure);
    }

    @GET
    @Path("invalid-return")
    @NotNull
    @Operation(hidden = true)
    public String invalidReturn() {
        return null;
    }

    public record Input(@NotNull String name, int count) {
    }

    @POST
    @Consumes("application/json")
    @Operation(hidden = true)
    public Uni<Response> input(@Valid Input input) {
        return Uni.createFrom().item(Response.ok().build());
    }
}
