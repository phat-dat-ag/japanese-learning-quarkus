package com.japaneselearning.quiz.player.resource;

import com.japaneselearning.common.resource.BaseResource;
import com.japaneselearning.quiz.player.dto.QuizAnswerRequest;
import com.japaneselearning.quiz.player.dto.QuizSessionCreateRequest;
import com.japaneselearning.quiz.player.service.QuizAnswerService;
import com.japaneselearning.quiz.player.service.QuizGameService;

import io.smallrye.mutiny.Uni;

import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Tag(name = "Kanji Quiz")
@Path("/api/v1/kanji-quiz")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"User", "Admin"})
public class KanjiQuizResource extends BaseResource {
    private final QuizGameService games;
    private final JsonWebToken jwt;
    private final QuizAnswerService answers;

    public KanjiQuizResource(
            QuizGameService games,
            JsonWebToken jwt,
            QuizAnswerService answers
    ) {
        this.games = games;
        this.jwt = jwt;
        this.answers = answers;
    }

    @GET
    @Path("/config")
    @Operation(summary = "Get playable Kanji Quiz configuration")
    public Uni<Response> quizConfiguration() {
        return games.configuration().map(this::success);
    }

    @POST
    @Path("/sessions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a randomized Kanji Quiz session")
    @RequestBody(required = true)
    public Uni<Response> createQuizSession(
            @NotNull
            @Valid
            QuizSessionCreateRequest request
    ) {
        return games.create(subject(), request).map(this::success);
    }

    @GET
    @Path("/sessions/{id}")
    @Operation(summary = "Get an owned Kanji Quiz session")
    public Uni<Response> quizSession(
            @PathParam("id")
            @Positive
            Long id
    ) {
        return games.session(subject(), id).map(this::success);
    }

    @GET
    @Path("/sessions/{id}/next")
    @Operation(summary = "Get the next unanswered Kanji Quiz snapshot")
    public Uni<Response> nextQuizQuestion(
            @PathParam("id")
            @Positive Long id
    ) {
        return games.next(subject(), id).map(this::success);
    }

    @POST
    @Path("/sessions/{id}/answers")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Submit the next Kanji Quiz snapshot answer")
    @RequestBody(required = true)
    public Uni<Response> submitQuizAnswer(
            @PathParam("id")
            @Positive
            Long id,

            @NotNull
            @Valid
            QuizAnswerRequest request
    ) {
        return answers.submit(subject(), id, request).map(this::success);
    }

    @POST
    @Path("/sessions/{id}/finish")
    @Operation(summary = "Complete an answered Kanji Quiz session")
    public Uni<Response> finishQuizSession(
            @PathParam("id")
            @Positive
            Long id
    ) {
        return games.finish(subject(), id).map(this::success);
    }

    private String subject() {
        String subject = jwt.getSubject();
        if (
                subject == null
                        || subject.isBlank()
                        || subject.codePointCount(0, subject.length()) > 255
        ) {
            throw new NotAuthorizedException("Bearer");
        }

        return subject;
    }
}
