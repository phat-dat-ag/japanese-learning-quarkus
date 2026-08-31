package com.japaneselearning.vocabulary.resource;

import com.japaneselearning.vocabulary.dto.LessonResponse;
import com.japaneselearning.vocabulary.service.LessonService;

import io.smallrye.mutiny.Uni;

import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/v1/lessons")
@Produces(MediaType.APPLICATION_JSON)
public class LessonResource {

    private final LessonService lessonService;

    public LessonResource(LessonService lessonService) {
        this.lessonService = lessonService;
    }

    @GET
    public Uni<List<LessonResponse>> getLessons(
            @QueryParam("level") @NotBlank(message = "Level is required") String level
    ) {

        return lessonService.getLessonsByLevel(level.trim());
    }
}