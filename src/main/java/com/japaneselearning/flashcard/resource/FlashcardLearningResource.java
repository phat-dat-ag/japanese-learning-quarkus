package com.japaneselearning.flashcard.resource;

import com.japaneselearning.common.resource.BaseResource;
import com.japaneselearning.flashcard.domain.FlashcardSessionStatus;
import com.japaneselearning.flashcard.dto.FlashcardReviewRequest;
import com.japaneselearning.flashcard.dto.FlashcardSessionCreateRequest;
import com.japaneselearning.flashcard.repository.FlashcardLearningRepository;
import com.japaneselearning.flashcard.service.FlashcardReviewService;
import com.japaneselearning.flashcard.service.FlashcardSessionService;
import com.japaneselearning.flashcard.service.LearningStatisticsService;
import com.japaneselearning.flashcard.service.VocabularyBookmarkService;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/flashcards")
@Tag(name = "Flashcard Learning")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"User", "Admin"})
public class FlashcardLearningResource extends BaseResource {
    private final FlashcardIdentity identity;
    private final FlashcardSessionService sessions;
    private final FlashcardReviewService reviews;
    private final VocabularyBookmarkService bookmarks;
    private final LearningStatisticsService learning;

    public FlashcardLearningResource(
            FlashcardIdentity identity,
            FlashcardSessionService sessions,
            FlashcardReviewService reviews,
            VocabularyBookmarkService bookmarks,
            LearningStatisticsService learning
    ) {
        this.identity = identity;
        this.sessions = sessions;
        this.reviews = reviews;
        this.bookmarks = bookmarks;
        this.learning = learning;
    }

    @POST
    @Path("/sessions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Start a lesson flashcard session")
    @RequestBody(required = true)
    public Uni<Response> createFlashcardSession(
            @NotNull
            @Valid
            FlashcardSessionCreateRequest request
    ) {
        return sessions
                .create(identity.subject(), request)
                .map(this::personal);
    }

    @GET
    @Path("/sessions")
    @Operation(summary = "List owned flashcard sessions")
    public Uni<Response> listFlashcardSessions(
            @QueryParam("lessonId")
            @Positive
            Long lessonId,

            @QueryParam("status")
            FlashcardSessionStatus status,

            @QueryParam("page")
            @DefaultValue("0")
            int page,

            @QueryParam("size")
            @DefaultValue("20")
            int size
    ) {
        return sessions
                .list(identity.subject(), lessonId, status, page, size)
                .map(this::personal);
    }

    @GET
    @Path("/sessions/{id}")
    @Operation(summary = "Resume an owned flashcard session")
    public Uni<Response> getFlashcardSession(
            @PathParam("id")
            @Positive
            Long id
    ) {
        return sessions
                .get(identity.subject(), id)
                .map(this::personal);
    }

    @PUT
    @Path("/sessions/{id}/items/{itemId}/review")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Rate a flashcard session item idempotently")
    @RequestBody(required = true)
    public Uni<Response> reviewFlashcard(
            @PathParam("id")
            @Positive
            Long id,

            @PathParam("itemId")
            @Positive Long itemId,

            @NotNull
            @Valid
            FlashcardReviewRequest request
    ) {
        return reviews
                .submit(identity.subject(), id, itemId, request)
                .map(this::personal);
    }

    @POST
    @Path("/sessions/{id}/finish")
    @Operation(summary = "Confirm completion of an owned flashcard session")
    public Uni<Response> finishFlashcardSession(
            @PathParam("id")
            @Positive
            Long id
    ) {
        return sessions
                .finish(identity.subject(), id)
                .map(this::personal);
    }

    @POST
    @Path("/sessions/{id}/abandon")
    @Operation(summary = "Abandon an owned flashcard session idempotently")
    public Uni<Response> abandonFlashcardSession(
            @PathParam("id")
            @Positive
            Long id
    ) {
        return sessions
                .abandon(identity.subject(), id)
                .map(this::personal);
    }

    @GET
    @Path("/reviews/due")
    @Operation(summary = "List vocabulary due for scheduled review")
    public Uni<Response> dueFlashcards(
            @QueryParam("level")
            String level,

            @QueryParam("lessonId")
            @Positive
            Long lessonId,

            @QueryParam("page")
            @DefaultValue("0")
            int page,

            @QueryParam("size")
            @DefaultValue("20")
            int size
    ) {
        return learning
                .list(
                        identity.subject(),
                        level,
                        lessonId,
                        FlashcardLearningRepository.Collection.DUE,
                        page,
                        size
                )
                .map(this::personal);
    }

    @PUT
    @Path("/{vocabularyId}/bookmark")
    @Operation(summary = "Bookmark vocabulary idempotently")
    public Uni<Response> addFlashcardBookmark(
            @PathParam("vocabularyId")
            @Positive
            Long vocabularyId
    ) {
        return bookmarks
                .add(identity.subject(), vocabularyId)
                .map(this::personal);
    }

    @DELETE
    @Path("/{vocabularyId}/bookmark")
    @Operation(summary = "Remove a vocabulary bookmark idempotently")
    public Uni<Response> removeFlashcardBookmark(
            @PathParam("vocabularyId")
            @Positive
            Long vocabularyId
    ) {
        return bookmarks
                .remove(identity.subject(), vocabularyId)
                .map(this::personal);
    }

    @GET
    @Path("/bookmarks")
    @Operation(summary = "List bookmarked vocabulary")
    public Uni<Response> bookmarkedFlashcards(
            @QueryParam("level")
            String level,

            @QueryParam("lessonId")
            @Positive
            Long lessonId,

            @QueryParam("page")
            @DefaultValue("0")
            int page,

            @QueryParam("size")
            @DefaultValue("20")
            int size
    ) {
        return learning
                .list(
                        identity.subject(),
                        level,
                        lessonId,
                        FlashcardLearningRepository.Collection.BOOKMARKS,
                        page,
                        size
                )
                .map(this::personal);
    }

    @GET
    @Path("/statistics")
    @Operation(summary = "Get personal vocabulary learning statistics")
    public Uni<Response> flashcardStatistics(
            @QueryParam("level")
            String level,

            @QueryParam("lessonId")
            @Positive
            Long lessonId
    ) {
        return learning
                .statistics(identity.subject(), level, lessonId)
                .map(this::personal);
    }

    @GET
    @Path("/reviews/forgotten")
    @Operation(summary = "List vocabulary ranked by AGAIN history")
    public Uni<Response> forgottenFlashcards(
            @QueryParam("level")
            String level,

            @QueryParam("lessonId")
            @Positive
            Long lessonId,

            @QueryParam("page")
            @DefaultValue("0")
            int page,

            @QueryParam("size")
            @DefaultValue("20")
            int size
    ) {
        return learning
                .list(
                        identity.subject(),
                        level,
                        lessonId,
                        FlashcardLearningRepository.Collection.FORGOTTEN,
                        page,
                        size
                )
                .map(this::personal);
    }

    private Response personal(Object data) {
        return Response.fromResponse(success(data))
                .header("Cache-Control", "private, no-store")
                .build();
    }
}
