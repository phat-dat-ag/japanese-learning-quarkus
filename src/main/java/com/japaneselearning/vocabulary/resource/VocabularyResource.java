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
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;

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
            summary = "Import a vocabulary batch (Admin only)",
            description = "New vocabulary is fully created. Existing vocabulary must have identical level and lesson sets; "
                    + "only new examples are appended. Assignment mismatches reject the entire batch.")
    public Uni<Response> createVocabularies(List<VocabularyImportItem> items) {
        return vocabularyService.importVocabulary(items).map(this::success);
    }

    @POST
    @RolesAllowed("Admin")
    @Operation(summary = "Import a vocabulary file (Admin only)",
            description = "Uses the same atomic business rules as JSON batches: full creation for new vocabulary, "
                    + "examples only for existing vocabulary with matching level and lesson sets.")
    @Path("/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
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
