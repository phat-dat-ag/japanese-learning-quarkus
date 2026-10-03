package com.japaneselearning.vocabulary.admin.resource;

import com.japaneselearning.common.resource.BaseResource;
import com.japaneselearning.vocabulary.admin.dto.VocabularyExampleUpdateRequest;
import com.japaneselearning.vocabulary.admin.service.VocabularyExampleEditService;
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
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Admin Vocabulary Examples")
@Path("/api/v1/admin/vocabularies/{vocabularyId}")
@RolesAllowed("Admin")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AdminVocabularyExampleResource extends BaseResource {
    private final VocabularyExampleEditService exampleService;

    public AdminVocabularyExampleResource(VocabularyExampleEditService exampleService) {
        this.exampleService = exampleService;
    }

    @POST
    @Path("/examples")
    @Operation(
            summary = "Add examples; duplicate content identity returns 409",
            description = "Body must be an array of 1 to 100 items, even for one item. All additions are atomic."
    )
    @RequestBody(required = true)
    public Uni<Response> addVocabularyExamples(
            @PathParam("vocabularyId")
            @Positive
            @Parameter(description = "Target vocabulary ID", required = true)
            Long vocabularyId,

            @NotEmpty
            @Size(max = 100)
            List<@NotNull @Valid VocabularyExampleUpdateRequest> request
    ) {
        return exampleService
                .addVocabularyExamples(vocabularyId, request)
                .map(this::success);
    }

    @PUT
    @Path("/examples/{exampleId}")
    @Operation(
            summary = "Update one associated example; shared sentence content cannot be changed",
            description = "Updates exactly one resource or assignment, not the vocabulary aggregate."
    )
    @RequestBody(required = true)
    public Uni<Response> updateVocabularyExample(
            @PathParam("vocabularyId")
            @Positive
            @Parameter(description = "Target vocabulary ID", required = true)
            Long vocabularyId,

            @PathParam("exampleId")
            @Positive
            @Parameter(description = "Existing exampleId", required = true)
            Long exampleId,

            @NotNull
            @Valid
            VocabularyExampleUpdateRequest request
    ) {
        return exampleService
                .updateVocabularyExample(vocabularyId, exampleId, request)
                .map(this::success);
    }
}
