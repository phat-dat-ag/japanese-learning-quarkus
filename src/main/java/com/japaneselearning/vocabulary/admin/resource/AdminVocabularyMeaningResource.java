package com.japaneselearning.vocabulary.admin.resource;

import com.japaneselearning.common.resource.BaseResource;
import com.japaneselearning.vocabulary.admin.dto.VocabularyMeaningUpdateRequest;
import com.japaneselearning.vocabulary.admin.service.VocabularyMeaningEditService;
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

@Tag(name = "Admin Vocabulary Meanings")
@Path("/api/v1/admin/vocabularies/{vocabularyId}")
@RolesAllowed("Admin")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AdminVocabularyMeaningResource extends BaseResource {
    private final VocabularyMeaningEditService meaningService;

    public AdminVocabularyMeaningResource(VocabularyMeaningEditService meaningService) {
        this.meaningService = meaningService;
    }

    @POST
    @Path("/meanings")
    @Operation(
            summary = "Add meanings",
            description = "Body must be an array of 1 to 100 items, even for one item. All additions are atomic."
    )
    @RequestBody(required = true)
    public Uni<Response> addVocabularyMeanings(
            @PathParam("vocabularyId")
            @Positive
            @Parameter(description = "Target vocabulary ID", required = true)
            Long vocabularyId,

            @NotEmpty
            @Size(max = 100)
            List<@NotNull @Valid VocabularyMeaningUpdateRequest> request
    ) {
        return meaningService
                .addVocabularyMeanings(vocabularyId, request)
                .map(this::success);
    }

    @PUT
    @Path("/meanings/{meaningId}")
    @Operation(
            summary = "Update one owned meaning",
            description = "Updates exactly one resource or assignment, not the vocabulary aggregate."
    )
    @RequestBody(required = true)
    public Uni<Response> updateVocabularyMeaning(
            @PathParam("vocabularyId")
            @Positive
            @Parameter(description = "Target vocabulary ID", required = true)
            Long vocabularyId,

            @PathParam("meaningId")
            @Positive
            @Parameter(description = "Existing meaningId", required = true)
            Long meaningId,

            @NotNull
            @Valid
            VocabularyMeaningUpdateRequest request
    ) {
        return meaningService
                .updateVocabularyMeaning(vocabularyId, meaningId, request)
                .map(this::success);
    }
}
