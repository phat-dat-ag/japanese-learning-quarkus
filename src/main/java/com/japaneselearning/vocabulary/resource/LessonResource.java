package com.japaneselearning.vocabulary.resource;

import com.japaneselearning.common.resource.BaseResource;
import com.japaneselearning.vocabulary.dto.LessonWriteRequest;
import com.japaneselearning.vocabulary.service.LessonBatchFileReader;
import com.japaneselearning.vocabulary.service.LessonBatchService;
import com.japaneselearning.vocabulary.service.LessonService;
import com.japaneselearning.vocabulary.service.LessonContentService;
import com.japaneselearning.vocabulary.dto.content.LessonContentWriteRequest;
import com.japaneselearning.vocabulary.dto.content.LessonContentResponse;
import com.japaneselearning.vocabulary.dto.content.LessonContentImportResponse.Mode;
import com.japaneselearning.vocabulary.service.LessonContentImportReader;
import com.japaneselearning.vocabulary.service.LessonContentImportService;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.DefaultValue;
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

@Tag(name = "Lessons")
@Path("/api/v1/lessons")
@Produces(MediaType.APPLICATION_JSON)
public class LessonResource extends BaseResource {

    private final LessonService lessonService;
    private final LessonBatchService lessonBatchService;
    private final LessonBatchFileReader batchFileReader;
    private final LessonContentService contentService;
    private final LessonContentImportReader importReader;
    private final LessonContentImportService importService;

    public LessonResource(
            LessonService lessonService,
            LessonBatchService lessonBatchService,
            LessonBatchFileReader batchFileReader,
            LessonContentService contentService,
            LessonContentImportReader importReader,
            LessonContentImportService importService
    ) {
        this.lessonService = lessonService;
        this.lessonBatchService = lessonBatchService;
        this.batchFileReader = batchFileReader;
        this.contentService = contentService;
        this.importReader = importReader;
        this.importService = importService;
    }

    @POST
    @Path("/content/import")
    @RolesAllowed("Admin")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Import complete grammar lessons from JSON (Admin only)")
    public Uni<Response> importLessonContents(
            @RestForm("file")
            @NotNull
            FileUpload file,

            @QueryParam("mode")
            @DefaultValue("SKIP_EXISTING")
            Mode mode
    ) {
        return importReader
                .read(file.uploadedFile())
                .chain(items ->
                        importService.importLessons(items, mode)
                )
                .map(this::success);
    }

    @GET
    @Path("/{lessonId}")
    @Operation(summary = "Retrieve lesson metadata (User or Admin)")
    public Uni<Response> getLesson(
            @PathParam("lessonId")
            @Positive
            Long lessonId
    ) {
        return lessonService
                .getLesson(lessonId)
                .map(this::success);
    }

    @DELETE
    @Path("/{lessonId}")
    @RolesAllowed("Admin")
    @Operation(summary = "Delete an unreferenced lesson (Admin only)")
    public Uni<Response> deleteLesson(
            @PathParam("lessonId")
            @Positive
            Long lessonId
    ) {
        return lessonService
                .deleteLesson(lessonId)
                .map(this::success);
    }

    @GET
    @Path("/{lessonId}/content")
    @Operation(summary = "Retrieve structured grammar content (User or Admin)")
    public Uni<Response> getLessonContent(
            @PathParam("lessonId")
            @Positive
            Long lessonId
    ) {
        return contentService
                .getContent(lessonId)
                .map(this::contentResponse);
    }

    @PUT
    @Path("/{lessonId}/content")
    @RolesAllowed("Admin")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create or replace structured grammar content (Admin only)")
    public Uni<Response> putLessonContent(
            @PathParam("lessonId")
            @Positive
            Long lessonId,

            @NotNull
            @Valid
            LessonContentWriteRequest request,

            @HeaderParam("If-Match")
            String ifMatch
    ) {
        return contentService
                .putContent(lessonId, request, ifMatch)
                .map(this::contentResponse);
    }

    @DELETE
    @Path("/{lessonId}/content")
    @RolesAllowed("Admin")
    @Operation(summary = "Remove grammar content at the supplied revision (Admin only)")
    public Uni<Response> deleteLessonContent(
            @PathParam("lessonId")
            @Positive
            Long lessonId,

            @QueryParam("expectedVersion")
            @NotNull @Positive
            Long expectedVersion,

            @HeaderParam("If-Match")
            String ifMatch
    ) {
        return contentService
                .deleteContent(lessonId, expectedVersion, ifMatch)
                .map(this::success);
    }

    private Response contentResponse(LessonContentResponse content) {
        return Response.fromResponse(success(content))
                .header("ETag", content.etag())
                .build();
    }

    @POST
    @Path("/batch")
    @RolesAllowed("Admin")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Create lessons independently (Admin only)")
    @RequestBody(required = true)
    public Uni<Response> createLessons(
            @NotNull
            @RestForm("file")
            FileUpload file
    ) {
        return batchFileReader
                .read(file.uploadedFile())
                .flatMap(lessonBatchService::createLessons)
                .map(this::success);
    }

    @POST
    @RolesAllowed("Admin")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a lesson")
    @RequestBody(required = true)
    public Uni<Response> createLesson(
            @NotNull @Valid LessonWriteRequest request
    ) {
        return lessonService
                .createLesson(request)
                .map(this::success);
    }

    @PUT
    @Path("/{lessonId}")
    @RolesAllowed("Admin")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update a lesson")
    @RequestBody(required = true)
    public Uni<Response> updateLesson(
            @PathParam("lessonId") @Positive Long lessonId,
            @NotNull @Valid LessonWriteRequest request
    ) {
        return lessonService
                .updateLesson(lessonId, request)
                .map(this::success);
    }

    @GET
    @Operation(summary = "List lessons by JLPT level")
    public Uni<Response> getLessons(
            @QueryParam("level")
            @NotBlank(message = "Level is required") String level
    ) {

        return lessonService
                .getLessonsByLevel(level.trim())
                .map(this::success);
    }
}
