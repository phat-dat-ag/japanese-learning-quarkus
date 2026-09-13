package com.japaneselearning.flashcard.resource;

import com.japaneselearning.common.resource.BaseResource;
import com.japaneselearning.flashcard.service.FlashcardService;

import io.smallrye.mutiny.Uni;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/flashcards")
@Produces(MediaType.APPLICATION_JSON)
public class FlashcardResource extends BaseResource {

    private final FlashcardService flashcardService;

    public FlashcardResource(
            FlashcardService flashcardService
    ) {
        this.flashcardService = flashcardService;
    }

    @GET
    public Uni<Response> getFlashcards(
            @QueryParam("level") String level,
            @QueryParam("lesson") Integer lesson,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size
    ) {
        return flashcardService
                .getFlashcards(level, lesson, page, size)
                .map(this::success);
    }

    @GET
    @Path("/{id}")
    public Uni<Response> getFlashcard(
            @PathParam("id") Long flashcardId
    ) {
        return flashcardService
                .getFlashcard(flashcardId)
                .map(this::success);
    }
}