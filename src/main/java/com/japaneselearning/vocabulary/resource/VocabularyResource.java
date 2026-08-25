package com.japaneselearning.vocabulary.resource;

import com.japaneselearning.vocabulary.service.VocabularyService;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/api/vocabularies")
@Produces(MediaType.APPLICATION_JSON)
public class VocabularyResource {

    private final VocabularyService vocabularyService;

    public VocabularyResource(VocabularyService vocabularyService) {
        this.vocabularyService = vocabularyService;
    }

    @POST
    @Path("/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Uni<Response> importVocabulary(
            @org.jboss.resteasy.reactive.RestForm("file")
            FileUpload file) {

        return vocabularyService
                .importVocabulary(file.uploadedFile())
                .map(result ->
                        Response.ok(result).build()
                );
    }
}