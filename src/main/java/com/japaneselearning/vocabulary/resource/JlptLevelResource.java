package com.japaneselearning.vocabulary.resource;

import com.japaneselearning.common.resource.BaseResource;
import com.japaneselearning.vocabulary.service.JlptLevelService;

import io.smallrye.mutiny.Uni;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/jlpt-levels")
@Produces(MediaType.APPLICATION_JSON)
public class JlptLevelResource extends BaseResource {

    private final JlptLevelService jlptLevelService;

    public JlptLevelResource(
            JlptLevelService jlptLevelService
    ) {
        this.jlptLevelService = jlptLevelService;
    }

    @GET
    public Uni<Response> getLevels() {
        return jlptLevelService
                .getLevels()
                .map(this::success);
    }
}