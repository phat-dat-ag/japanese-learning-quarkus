package com.japaneselearning.vocabulary.admin.resource;

import com.japaneselearning.common.resource.BaseResource;
import com.japaneselearning.vocabulary.admin.dto.VocabularyPitchAccentUpdateRequest;
import com.japaneselearning.vocabulary.admin.service.VocabularyPitchAccentEditService;
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

@Tag(name = "Admin Vocabulary Pitch Accents")
@Path("/api/v1/admin/vocabularies/{vocabularyId}")
@RolesAllowed("Admin")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AdminVocabularyPitchAccentResource extends BaseResource {
    private final VocabularyPitchAccentEditService pitchAccentService;

    public AdminVocabularyPitchAccentResource(VocabularyPitchAccentEditService pitchAccentService) {
        this.pitchAccentService = pitchAccentService;
    }

    @POST
    @Path("/pitch-accents")
    @Operation(
            summary = "Add pitch accents to owned readings",
            description = "Body must be an array of 1 to 100 items, even for one item. All additions are atomic."
    )
    @RequestBody(required = true)
    public Uni<Response> addVocabularyPitchAccents(
            @PathParam("vocabularyId")
            @Positive
            @Parameter(description = "Target vocabulary ID", required = true)
            Long vocabularyId,

            @NotEmpty
            @Size(max = 100)
            List<@NotNull @Valid VocabularyPitchAccentUpdateRequest> request
    ) {
        return pitchAccentService
                .addVocabularyPitchAccents(vocabularyId, request)
                .map(this::success);
    }

    @PUT
    @Path("/pitch-accents/{pitchAccentId}")
    @Operation(
            summary = "Update one owned pitch accent",
            description = "Updates exactly one resource or assignment, not the vocabulary aggregate."
    )
    @RequestBody(required = true)
    public Uni<Response> updateVocabularyPitchAccent(
            @PathParam("vocabularyId")
            @Positive
            @Parameter(description = "Target vocabulary ID", required = true)
            Long vocabularyId,

            @PathParam("pitchAccentId")
            @Positive
            @Parameter(description = "Existing pitchAccentId", required = true)
            Long pitchAccentId,

            @NotNull
            @Valid
            VocabularyPitchAccentUpdateRequest request
    ) {
        return pitchAccentService
                .updateVocabularyPitchAccent(vocabularyId, pitchAccentId, request)
                .map(this::success);
    }
}
