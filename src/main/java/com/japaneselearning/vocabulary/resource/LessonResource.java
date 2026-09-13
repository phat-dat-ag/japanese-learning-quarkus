package com.japaneselearning.vocabulary.resource;

import com.japaneselearning.common.resource.BaseResource;
import com.japaneselearning.vocabulary.service.LessonService;

import io.smallrye.mutiny.Uni;

import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/lessons")
@Produces(MediaType.APPLICATION_JSON)
public class LessonResource extends BaseResource {

    private final LessonService lessonService;

    public LessonResource(LessonService lessonService) {
        this.lessonService = lessonService;
    }

    @GET
    public Uni<Response> getLessons(
            @QueryParam("level")
            @NotBlank(message = "Level is required") String level
    ) {

        return lessonService
                .getLessonsByLevel(level.trim())
                .map(this::success);
    }
}