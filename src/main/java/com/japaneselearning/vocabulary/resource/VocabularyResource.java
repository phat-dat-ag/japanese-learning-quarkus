package com.japaneselearning.vocabulary.resource;

import com.japaneselearning.common.resource.BaseResource;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.service.VocabularyService;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;

@Tag(name = "Vocabulary Imports")
@Path("/api/vocabularies")
@Produces(MediaType.APPLICATION_JSON)
public class VocabularyResource extends BaseResource {

    private final VocabularyService vocabularyService;

    public VocabularyResource(VocabularyService vocabularyService) {
        this.vocabularyService = vocabularyService;
    }

    @POST
    @RolesAllowed("Admin")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Import a vocabulary batch (Admin only)"
    )
    @RequestBody(required = true)
    public Uni<Response> createVocabularies(
            List<VocabularyImportItem> items
    ) {
        return vocabularyService
                .importVocabulary(items)
                .map(this::success);
    }

    @POST
    @RolesAllowed("Admin")
    @Operation(summary = "Import a vocabulary file (Admin only)")
    @Path("/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RequestBody(required = true)
    public Uni<Response> importVocabularies(
            @NotNull
            @RestForm("file")
            FileUpload file
    ) {

        return vocabularyService
                .importVocabulary(file.uploadedFile())
                .map(this::success);
    }
}
