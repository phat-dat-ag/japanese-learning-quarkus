package com.japaneselearning.quiz.admin.resource;

import com.japaneselearning.common.resource.BaseResource;
import com.japaneselearning.quiz.admin.dto.QuizCorrectOptionRequest;
import com.japaneselearning.quiz.admin.dto.QuizOptionTextRequest;
import com.japaneselearning.quiz.admin.dto.QuizQuestionCreateRequest;
import com.japaneselearning.quiz.admin.dto.QuizQuestionFilter;
import com.japaneselearning.quiz.admin.dto.QuizQuestionUpdateRequest;
import com.japaneselearning.quiz.admin.dto.QuizVersionRequest;
import com.japaneselearning.quiz.admin.service.QuizAdminQueryService;
import com.japaneselearning.quiz.admin.service.QuizAdminWriteService;
import com.japaneselearning.quiz.admin.service.QuizImportFileReader;
import com.japaneselearning.quiz.admin.service.QuizImportService;
import com.japaneselearning.quiz.domain.QuestionSource;
import com.japaneselearning.quiz.domain.QuestionStatus;
import com.japaneselearning.quiz.domain.QuizQuestionRules;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Tag(name = "Admin Kanji Quiz")
@Path("/api/v1/admin/kanji-quiz/questions")
@RolesAllowed("Admin")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AdminKanjiQuizResource extends BaseResource {
    private final QuizAdminQueryService queries;
    private final QuizAdminWriteService writes;
    private final QuizImportFileReader importReader;
    private final QuizImportService importer;

    public AdminKanjiQuizResource(
            QuizAdminQueryService queries,
            QuizAdminWriteService writes,
            QuizImportFileReader importReader,
            QuizImportService importer
    ) {
        this.queries = queries;
        this.writes = writes;
        this.importReader = importReader;
        this.importer = importer;
    }

    @POST
    @Path("/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Import draft Kanji Quiz questions with per-item results")
    @RequestBody(required = true)
    public Uni<Response> importQuizQuestions(
            @NotNull
            @RestForm("file")
            FileUpload file
    ) {
        return importReader
                .read(file.uploadedFile(), file.contentType())
                .chain(importer::importQuestions)
                .map(this::success);
    }

    @POST
    @Operation(summary = "Create a draft Kanji Quiz question")
    @RequestBody(required = true)
    public Uni<Response> createQuizQuestion(
            @NotNull
            @Valid
            QuizQuestionCreateRequest request
    ) {
        return writes.create(request).map(this::success);
    }

    @GET
    @Operation(summary = "Search the Admin question bank")
    public Uni<Response> listQuizQuestions(
            @QueryParam("sourceType") String sourceType,
            @QueryParam("status") String status,
            @QueryParam("levelId") @Positive Long levelId,
            @QueryParam("lessonId") @Positive Long lessonId,
            @QueryParam("vocabularyId") @Positive Long vocabularyId,
            @QueryParam("exampleId") @Positive Long exampleId,
            @QueryParam("keyword") @Size(max = 200) String keyword,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size
    ) {
        QuizQuestionFilter filter = new QuizQuestionFilter(
                enumValue(QuestionSource.class, sourceType, "sourceType"),
                enumValue(QuestionStatus.class, status, "status"),
                levelId, lessonId, vocabularyId, exampleId, keyword, page, size
        );

        return queries.list(filter).map(this::success);
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get full Admin question details and correct answer")
    public Uni<Response> getQuizQuestion(
            @PathParam("id")
            @Positive
            Long questionId
    ) {
        return queries.detail(questionId).map(this::success);
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Replace question content and demote to draft")
    @RequestBody(required = true)
    public Uni<Response> updateQuizQuestion(
            @PathParam("id")
            @Positive
            Long id,

            @NotNull
            @Valid
            QuizQuestionUpdateRequest request
    ) {
        return writes.update(id, request).map(this::success);
    }

    @PUT
    @Path("/{id}/options/{optionId}")
    @Operation(summary = "Update one owned option text")
    @RequestBody(required = true)
    public Uni<Response> updateQuizOption(
            @PathParam("id")
            @Positive
            Long id,

            @PathParam("optionId")
            @Positive
            Long optionId,

            @NotNull
            @Valid
            QuizOptionTextRequest request
    ) {
        return writes.updateOption(id, optionId, request).map(this::success);
    }

    @PUT
    @Path("/{id}/correct-option")
    @Operation(summary = "Atomically change the correct option")
    @RequestBody(required = true)
    public Uni<Response> changeQuizCorrectOption(
            @PathParam("id")
            @Positive
            Long id,

            @NotNull
            @Valid
            QuizCorrectOptionRequest request
    ) {
        return writes.correctOption(id, request).map(this::success);
    }

    @POST
    @Path("/{id}/publish")
    @Operation(summary = "Publish a Kanji Quiz question")
    @RequestBody(required = true)
    public Uni<Response> publishQuizQuestion(
            @PathParam("id")
            @Positive
            Long id,

            @NotNull
            @Valid
            QuizVersionRequest request
    ) {
        return writes.publish(id, request.version()).map(this::success);
    }

    @POST
    @Path("/{id}/unpublish")
    @Operation(summary = "Unpublish a Kanji Quiz question")
    @RequestBody(required = true)
    public Uni<Response> unpublishQuizQuestion(
            @PathParam("id")
            @Positive
            Long id,

            @NotNull
            @Valid
            QuizVersionRequest request
    ) {
        return writes.unpublish(id, request.version()).map(this::success);
    }

    @POST
    @Path("/{id}/archive")
    @Operation(summary = "Archive a Kanji Quiz question")
    @RequestBody(required = true)
    public Uni<Response> archiveQuizQuestion(
            @PathParam("id")
            @Positive
            Long id,

            @NotNull
            @Valid
            QuizVersionRequest request
    ) {
        return writes.archive(id, request.version()).map(this::success);
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value, String field) {
        if (value == null) {
            return null;
        }

        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException invalid) {
            throw QuizQuestionRules.invalid(field, "Unsupported " + field);
        }
    }
}
