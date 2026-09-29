package com.japaneselearning.vocabulary.admin.resource;

import com.japaneselearning.common.resource.BaseResource;
import com.japaneselearning.vocabulary.admin.dto.VocabularyReadingUpdateRequest;
import com.japaneselearning.vocabulary.admin.service.VocabularyReadingEditService;
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
public class AdminVocabularyReadingResource extends BaseResource {
    private final VocabularyReadingEditService readingService;

    public AdminVocabularyReadingResource(VocabularyReadingEditService readingService) {
        this.readingService = readingService;
    }

    @POST
    @Path("/readings")
    @Operation(
            summary = "Add readings",
            description = "Body must be an array of 1 to 100 items, even for one item. All additions are atomic."
    )
    @RequestBody(required = true)
    public Uni<Response> addVocabularyReadings(
            @PathParam("vocabularyId")
            @Positive
            @Parameter(description = "Target vocabulary ID", required = true)
            Long vocabularyId,

            @NotEmpty
            @Size(max = 100)
            List<@NotNull @Valid VocabularyReadingUpdateRequest> request
    ) {
        return readingService
                .addVocabularyReadings(vocabularyId, request)
                .map(this::success);
    }

    @PUT
    @Path("/readings/{readingId}")
    @Operation(
            summary = "Update one owned reading",
            description = "Updates exactly one resource or assignment, not the vocabulary aggregate."
    )
    @RequestBody(required = true)
    public Uni<Response> updateVocabularyReading(
            @PathParam("vocabularyId")
            @Positive
            @Parameter(description = "Target vocabulary ID", required = true)
            Long vocabularyId,

            @PathParam("readingId")
            @Positive
            @Parameter(description = "Existing readingId", required = true)
            Long readingId,

            @NotNull
            @Valid
            VocabularyReadingUpdateRequest request
    ) {
        return readingService
                .updateVocabularyReading(vocabularyId, readingId, request)
                .map(this::success);
    }
}
