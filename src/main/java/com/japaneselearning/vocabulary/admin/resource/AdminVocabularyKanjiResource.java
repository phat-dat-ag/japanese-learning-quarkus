package com.japaneselearning.vocabulary.admin.resource;

import com.japaneselearning.common.resource.BaseResource;
import com.japaneselearning.vocabulary.admin.dto.KanjiEdit;
import com.japaneselearning.vocabulary.admin.dto.KanjiReadingEdit;
import com.japaneselearning.vocabulary.admin.service.VocabularyKanjiEditService;
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
public class AdminVocabularyKanjiResource extends BaseResource {
    private final VocabularyKanjiEditService kanjiService;

    public AdminVocabularyKanjiResource(VocabularyKanjiEditService kanjiService) {
        this.kanjiService = kanjiService;
    }

    @POST
    @Path("/kanji")
    @Operation(
            summary = "Create or attach kanji; existing metadata must match",
            description = "Body must be an array of 1 to 100 items, even for one item. All additions are atomic."
    )
    @RequestBody(required = true)
    public Uni<Response> addVocabularyKanji(
            @PathParam("vocabularyId") @Positive
            @Parameter(description = "Target vocabulary ID", required = true)
            Long vocabularyId,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid KanjiEdit> request
    ) {
        return kanjiService
                .addVocabularyKanji(vocabularyId, request)
                .map(this::success);
    }

    @PUT
    @Path("/kanji/{kanjiId}")
    @Operation(
            summary = "Update associated kanji; shared metadata changes return 409",
            description = "Updates exactly one resource or assignment, not the vocabulary aggregate."
    )
    @RequestBody(required = true)
    public Uni<Response> updateVocabularyKanji(
            @PathParam("vocabularyId") @Positive
            @Parameter(description = "Target vocabulary ID", required = true)
            Long vocabularyId,
            @PathParam("kanjiId") @Positive
            @Parameter(description = "Existing kanjiId", required = true)
            Long kanjiId,
            @NotNull @Valid KanjiEdit request
    ) {
        return kanjiService
                .updateVocabularyKanji(vocabularyId, kanjiId, request)
                .map(this::success);
    }

    @POST
    @Path("/kanji/{kanjiId}/readings")
    @Operation(
            summary = "Add readings to exclusively associated kanji; shared kanji returns 409",
            description = "Body must be an array of 1 to 100 items, even for one item. All additions are atomic."
    )
    @RequestBody(required = true)
    public Uni<Response> addKanjiReadings(
            @PathParam("vocabularyId") @Positive
            @Parameter(description = "Target vocabulary ID", required = true)
            Long vocabularyId,
            @PathParam("kanjiId") @Positive
            @Parameter(description = "Existing kanjiId", required = true)
            Long kanjiId,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid KanjiReadingEdit> request
    ) {
        return kanjiService
                .addKanjiReadings(vocabularyId, kanjiId, request)
                .map(this::success);
    }

    @PUT
    @Path("/kanji/{kanjiId}/readings/{readingId}")
    @Operation(
            summary = "Update one reading of exclusively associated kanji; validate full parent chain",
            description = "Updates exactly one resource or assignment, not the vocabulary aggregate."
    )
    @RequestBody(required = true)
    public Uni<Response> updateKanjiReading(
            @PathParam("vocabularyId") @Positive
            @Parameter(description = "Target vocabulary ID", required = true)
            Long vocabularyId,
            @PathParam("kanjiId") @Positive
            @Parameter(description = "Existing kanjiId", required = true)
            Long kanjiId,
            @PathParam("readingId") @Positive
            @Parameter(description = "Existing readingId", required = true)
            Long readingId,
            @NotNull @Valid KanjiReadingEdit request
    ) {
        return kanjiService
                .updateKanjiReading(vocabularyId, kanjiId, readingId, request)
                .map(this::success);
    }
}
