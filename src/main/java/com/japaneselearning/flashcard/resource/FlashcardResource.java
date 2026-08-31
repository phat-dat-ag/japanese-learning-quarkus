package com.japaneselearning.flashcard.resource;

import com.japaneselearning.flashcard.dto.FlashcardDetailResponse;
import com.japaneselearning.flashcard.dto.FlashcardListResponse;
import com.japaneselearning.flashcard.service.FlashcardService;

import io.smallrye.mutiny.Uni;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/flashcards")
@Produces(MediaType.APPLICATION_JSON)
public class FlashcardResource {

    private final FlashcardService flashcardService;

    public FlashcardResource(
            FlashcardService flashcardService
    ) {
        this.flashcardService = flashcardService;
    }

    @GET
    public Uni<FlashcardListResponse> getFlashcards(
            @QueryParam("level") String level,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size
    ) {
        return flashcardService.getFlashcards(
                level,
                page,
                size
        );
    }

    @GET
    @Path("/{id}")
    public Uni<FlashcardDetailResponse> getFlashcard(
            @PathParam("id") Long id
    ) {
        return flashcardService.getFlashcard(id);
    }
}