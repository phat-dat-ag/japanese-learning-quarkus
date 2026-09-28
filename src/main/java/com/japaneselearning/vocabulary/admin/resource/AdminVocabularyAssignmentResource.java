package com.japaneselearning.vocabulary.admin.resource;

import com.japaneselearning.common.resource.BaseResource;
import com.japaneselearning.vocabulary.admin.dto.LessonAdd;
import com.japaneselearning.vocabulary.admin.dto.LevelAdd;
import com.japaneselearning.vocabulary.admin.dto.OrderEdit;
import com.japaneselearning.vocabulary.admin.dto.PosAdd;
import com.japaneselearning.vocabulary.admin.service.VocabularyAssignmentEditService;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.util.List;

@Path("/api/v1/admin/vocabularies/{vocabularyId}")
@RolesAllowed("Admin")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AdminVocabularyAssignmentResource extends BaseResource {
    private final VocabularyAssignmentEditService assignmentService;

    public AdminVocabularyAssignmentResource(VocabularyAssignmentEditService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @POST
    @Path("/levels")
    @Operation(
            summary = "Assign existing JLPT levels",
            description = "Body must be an array of 1 to 100 items, even for one item. All additions are atomic."
    )
    @RequestBody(required = true)
    public Uni<Response> addLevels(
            @PathParam("vocabularyId") @Positive
            @Parameter(description = "Target vocabulary ID", required = true)
            Long vocabularyId,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid LevelAdd> request) {
        return assignmentService.addLevels(vocabularyId, request).map(this::success);
    }

    @PUT
    @Path("/levels/{levelId}")
    @Operation(
            summary = "Update level assignment display order only",
            description = "Updates exactly one resource or assignment, not the vocabulary aggregate."
    )
    @RequestBody(required = true)
    public Uni<Response> updateLevel(
            @PathParam("vocabularyId") @Positive
            @Parameter(description = "Target vocabulary ID", required = true)
            Long vocabularyId,
            @PathParam("levelId") @Positive
            @Parameter(description = "Existing levelId", required = true)
            Long levelId,
            @NotNull @Valid OrderEdit request) {
        return assignmentService.updateLevel(vocabularyId, levelId, request).map(this::success);
    }

    @POST
    @Path("/lessons")
    @Operation(
            summary = "Assign existing lessons from assigned levels",
            description = "Body must be an array of 1 to 100 items, even for one item. All additions are atomic."
    )
    @RequestBody(required = true)
    public Uni<Response> addLessons(
            @PathParam("vocabularyId") @Positive
            @Parameter(description = "Target vocabulary ID", required = true)
            Long vocabularyId,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid LessonAdd> request) {
        return assignmentService.addLessons(vocabularyId, request).map(this::success);
    }

    @PUT
    @Path("/lessons/{lessonId}")
    @Operation(
            summary = "Update lesson assignment display order only; must be positive",
            description = "Updates exactly one resource or assignment, not the vocabulary aggregate."
    )
    @RequestBody(required = true)
    public Uni<Response> updateLesson(
            @PathParam("vocabularyId") @Positive
            @Parameter(description = "Target vocabulary ID", required = true)
            Long vocabularyId,
            @PathParam("lessonId") @Positive
            @Parameter(description = "Existing lessonId", required = true)
            Long lessonId,
            @NotNull @Valid OrderEdit request) {
        return assignmentService.updateLesson(vocabularyId, lessonId, request).map(this::success);
    }

    @POST
    @Path("/parts-of-speech")
    @Operation(
            summary = "Assign existing POS codes; assignments have no update fields",
            description = "Body must be an array of 1 to 100 items, even for one item. All additions are atomic."
    )
    @RequestBody(required = true)
    public Uni<Response> addPartsOfSpeech(
            @PathParam("vocabularyId") @Positive
            @Parameter(description = "Target vocabulary ID", required = true)
            Long vocabularyId,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid PosAdd> request) {
        return assignmentService.addPartsOfSpeech(vocabularyId, request).map(this::success);
    }
}
