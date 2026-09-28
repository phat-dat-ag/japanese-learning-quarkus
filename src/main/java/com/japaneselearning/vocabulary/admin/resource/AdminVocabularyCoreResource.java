package com.japaneselearning.vocabulary.admin.resource;

import com.japaneselearning.common.resource.BaseResource;
import com.japaneselearning.vocabulary.admin.dto.CoreEdit;
import com.japaneselearning.vocabulary.admin.service.VocabularyCoreEditService;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;


@Path("/api/v1/admin/vocabularies/{vocabularyId}")
@RolesAllowed("Admin")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AdminVocabularyCoreResource extends BaseResource {
    private final VocabularyCoreEditService coreService;

    public AdminVocabularyCoreResource(VocabularyCoreEditService coreService) {
        this.coreService = coreService;
    }

    @PUT
    @Operation(
            summary = "Update vocabulary core fields",
            description = "Updates exactly one resource or assignment, not the vocabulary aggregate."
    )
    @RequestBody(required = true)
    public Uni<Response> update(
            @PathParam("vocabularyId") @Positive
            @Parameter(description = "Target vocabulary ID", required = true)
            Long vocabularyId,
            @NotNull @Valid CoreEdit request) {
        return coreService.update(vocabularyId, request).map(this::success);
    }
}
