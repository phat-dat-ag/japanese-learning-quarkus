package com.japaneselearning.vocabulary.resource;

import com.japaneselearning.common.resource.BaseResource;
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
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/api/vocabularies")
@Produces(MediaType.APPLICATION_JSON)
public class VocabularyResource extends BaseResource {

    private final VocabularyService vocabularyService;

    public VocabularyResource(VocabularyService vocabularyService) {
        this.vocabularyService = vocabularyService;
    }

    @POST
    @RolesAllowed("Admin")
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