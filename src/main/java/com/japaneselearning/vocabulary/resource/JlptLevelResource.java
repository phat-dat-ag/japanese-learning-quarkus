package com.japaneselearning.vocabulary.resource;

import com.japaneselearning.vocabulary.dto.JlptLevelResponse;
import com.japaneselearning.vocabulary.service.JlptLevelService;

import io.smallrye.mutiny.Uni;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/v1/jlpt-levels")
@Produces(MediaType.APPLICATION_JSON)
public class JlptLevelResource {

    private final JlptLevelService jlptLevelService;

    public JlptLevelResource(
            JlptLevelService jlptLevelService
    ) {
        this.jlptLevelService = jlptLevelService;
    }

    @GET
    public Uni<List<JlptLevelResponse>> getLevels() {
        return jlptLevelService.getLevels();
    }
}