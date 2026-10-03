package com.japaneselearning.vocabulary.resource;

import com.japaneselearning.common.resource.BaseResource;
import com.japaneselearning.vocabulary.dto.LessonWriteRequest;
import com.japaneselearning.vocabulary.service.LessonBatchFileReader;
import com.japaneselearning.vocabulary.service.LessonBatchService;
import com.japaneselearning.vocabulary.service.LessonService;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.Consumes;
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

@Tag(name = "Lessons")
@Path("/api/v1/lessons")
@Produces(MediaType.APPLICATION_JSON)
public class LessonResource extends BaseResource {

    private final LessonService lessonService;
    private final LessonBatchService lessonBatchService;
    private final LessonBatchFileReader batchFileReader;

    public LessonResource(
            LessonService lessonService,
            LessonBatchService lessonBatchService,
            LessonBatchFileReader batchFileReader
    ) {
        this.lessonService = lessonService;
        this.lessonBatchService = lessonBatchService;
        this.batchFileReader = batchFileReader;
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