package com.japaneselearning.flashcard.resource;

import com.japaneselearning.common.resource.BaseResource;
import com.japaneselearning.flashcard.service.FlashcardService;
import com.japaneselearning.flashcard.service.PersonalFlashcardService;

import io.smallrye.mutiny.Uni;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Tag(name = "Flashcards")
@Path("/api/v1/flashcards")
@Produces(MediaType.APPLICATION_JSON)
public class FlashcardResource extends BaseResource {

    private final FlashcardService flashcardService;
    private final PersonalFlashcardService personalCards;
    private final FlashcardIdentity identity;

    public FlashcardResource(
            FlashcardService flashcardService,
            PersonalFlashcardService personalCards,
            FlashcardIdentity identity
    ) {
        this.flashcardService = flashcardService;
        this.personalCards = personalCards;
        this.identity = identity;
    }

    @GET
    @Operation(summary = "List flashcards")
    public Uni<Response> getFlashcards(
            @QueryParam("level")
            String level,

            @QueryParam("lesson")
            Integer lesson,

            @QueryParam("page")
            @DefaultValue("0")
            int page,

            @QueryParam("size")
            @DefaultValue("20")
            int size,

            @QueryParam("includeLearning")
            @DefaultValue("false")
            boolean includeLearning
    ) {
        if (includeLearning) {
            return personalCards.list(
                    identity.subject(), level, lesson, page, size
            ).map(this::personal);
        }

        return flashcardService
                .getFlashcards(level, lesson, page, size)
                .map(this::success);
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get vocabulary flashcard detail")
    public Uni<Response> getFlashcard(
            @PathParam("id")
            Long flashcardId,

            @QueryParam("includeLearning")
            @DefaultValue("false")
            boolean includeLearning
    ) {
        if (includeLearning) {
            return personalCards
                    .detail(identity.subject(), flashcardId)
                    .map(this::personal);
        }

        return flashcardService
                .getFlashcard(flashcardId)
                .map(this::success);
    }

    private Response personal(Object data) {
        return Response.fromResponse(success(data))
                .header("Cache-Control", "private, no-store")
                .build();
    }
}
