package com.japaneselearning.quiz.admin.service;

import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.quiz.admin.dto.QuizCorrectOptionRequest;
import com.japaneselearning.quiz.admin.dto.QuizOptionTextRequest;
import com.japaneselearning.quiz.admin.dto.QuizQuestionContentRequest;
import com.japaneselearning.quiz.admin.dto.QuizQuestionCreateRequest;
import com.japaneselearning.quiz.admin.dto.QuizQuestionResponse;
import com.japaneselearning.quiz.admin.dto.QuizQuestionUpdateRequest;
import com.japaneselearning.quiz.admin.repository.QuizAdminRepository;
import com.japaneselearning.quiz.domain.QuestionOption;
import com.japaneselearning.quiz.domain.QuestionSource;
import com.japaneselearning.quiz.domain.QuestionStatus;
import com.japaneselearning.quiz.domain.QuizQuestionRules;
import com.japaneselearning.quiz.entity.QuizQuestion;
import com.japaneselearning.quiz.entity.QuizQuestionOption;
import com.japaneselearning.quiz.repository.QuizQuestionOptionRepository;
import com.japaneselearning.quiz.repository.QuizQuestionRepository;
import com.japaneselearning.quiz.service.QuizPublicationService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.LockAcquisitionException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;

@ApplicationScoped
public class QuizAdminWriteService {
    private final QuizQuestionRepository questions;
    private final QuizQuestionOptionRepository options;
    private final QuizAdminRepository repository;
    private final QuizPublicationService publication;
    private final QuizAdminQueryService queries;

    public QuizAdminWriteService(
            QuizQuestionRepository questions,
            QuizQuestionOptionRepository options,
            QuizAdminRepository repository,
            QuizPublicationService publication,
            QuizAdminQueryService queries
    ) {
        this.questions = questions;
        this.options = options;
        this.repository = repository;
        this.publication = publication;
        this.queries = queries;
    }

    @WithTransaction
    public Uni<QuizQuestionResponse> create(QuizQuestionCreateRequest request) {
        QuizQuestion question = new QuizQuestion();

        question.status = QuestionStatus.DRAFT;
        apply(question, request.content());
        List<QuestionOption> values = request.options()
                .stream()
                .map(option -> new QuestionOption(option.text(), option.correct()))
                .toList();

        return complete(validateContent(question, request.content(), values)
                .chain(() -> questions.persist(question))
                .call(() -> Multi.createFrom().iterable(values)
                        .onItem().transformToUniAndConcatenate(value -> {
                            QuizQuestionOption option = new QuizQuestionOption();
                            option.questionId = question.id;
                            option.optionText = value.text();
                            option.correct = value.correct();
                            return options.persist(option);
                        }).collect().asList())
                .call(() -> repository.replaceClassifications(
                        question.id, request.content().levelIds(), request.content().lessonIds())));
    }

    @WithTransaction
    public Uni<QuizQuestionResponse> update(Long id, QuizQuestionUpdateRequest request) {
        return complete(editable(id, request.version()).chain(question -> {
            if (question.sourceType != request.content().sourceType()) {
                throw conflict("Source type cannot be changed");
            }

            return publication.prepareForEdit(id).invoke(value -> apply(value, request.content()))
                    .call(value -> options.findByQuestionId(id)
                            .chain(values -> validateContent(value, request.content(), domainOptions(values)))
                    )
                    .call(() -> repository.replaceClassifications(
                            id, request.content().levelIds(), request.content().lessonIds()
                    ));
        }));
    }

    @WithTransaction
    public Uni<QuizQuestionResponse> updateOption(Long id, Long optionId, QuizOptionTextRequest request) {
        return complete(editable(id, request.version()).chain(question ->
                options.findByQuestionId(id).chain(values -> {
                    QuizQuestionOption option = requireOption(values, optionId);

                    // Validate proposed values before flushing any demotion or option mutation.
                    List<QuestionOption> proposed = values.stream().map(value -> new QuestionOption(
                            value.id.equals(optionId) ? request.text() : value.optionText, value.correct)).toList();

                    QuizQuestionRules.validateOptions(proposed);

                    return publication.prepareForEdit(id).invoke(value -> option.optionText = request.text());
                })
        ));
    }

    @WithTransaction
    public Uni<QuizQuestionResponse> correctOption(Long id, QuizCorrectOptionRequest request) {
        return complete(editable(id, request.version()).chain(question ->
                options.findByQuestionId(id).chain(values -> {
                    QuizQuestionOption selected = requireOption(values, request.optionId());
                    QuizQuestionRules.validateOptions(values.stream().map(value ->
                                    new QuestionOption(value.optionText, value.id.equals(selected.id))
                            ).toList()
                    );

                    return publication.prepareForEdit(id)
                            .invoke(value -> values.forEach(option -> option.correct = false))
                            // Clear the old unique correct marker before setting the new one.
                            .call(options::flush)
                            .invoke(value -> selected.correct = true);
                })));
    }

    @WithTransaction
    public Uni<QuizQuestionResponse> publish(Long id, long version) {
        return complete(editable(id, version).chain(question ->
                publication.prepareForEdit(id).chain(() -> publication.publish(id)))
        );
    }

    @WithTransaction
    public Uni<QuizQuestionResponse> unpublish(Long id, long version) {
        return complete(locked(id, version).chain(question -> publication.prepareForEdit(id)));
    }

    @WithTransaction
    public Uni<QuizQuestionResponse> archive(Long id, long version) {
        return complete(locked(id, version).invoke(question -> {
            question.status = QuestionStatus.ARCHIVED;
            question.validatedExampleFingerprint = null;
        }));
    }

    private Uni<QuizQuestion> editable(Long id, long version) {
        return locked(id, version).invoke(question -> {
            if (question.status == QuestionStatus.ARCHIVED) {
                throw conflict("Unpublish the archived question to restore a draft before editing or publishing");
            }
        });
    }

    private Uni<QuizQuestion> locked(Long id, long version) {
        return questions.findByIdForUpdate(id).onItem().ifNull().failWith(() ->
                        new ResourceNotFoundException("QUIZ_QUESTION_NOT_FOUND", "Quiz question not found"))
                .invoke(question -> {
                    if (question.version != version) {
                        throw conflict("Question has changed; reload its current version");
                    }
                });
    }

    private Uni<Void> validateContent(
            QuizQuestion question,
            QuizQuestionContentRequest content,
            List<QuestionOption> values
    ) {
        validateIds(content.levelIds(), "levelIds");
        validateIds(content.lessonIds(), "lessonIds");

        Uni<String> reading;

        if (question.sourceType == QuestionSource.EXAMPLE) {
            if (question.exampleSentenceId == null || question.sentenceReading != null) {
                throw QuizQuestionRules.invalid(
                        "source",
                        "EXAMPLE requires exampleId and forbids sentenceReading"
                );
            }
            reading = questions.findExampleReadingForUpdate(question.exampleSentenceId)
                    .onItem().ifNull().failWith(() -> missing("Example"))
                    .map(source -> source.reading());
        } else {
            reading = Uni.createFrom().item(question.sentenceReading);
        }
        return reading.invoke(sentence -> QuizQuestionRules.validatePublication(question, sentence, values))
                .call(() -> question.vocabularyId == null ? Uni.createFrom().voidItem()
                        : requireReference("Vocabulary", question.vocabularyId)
                )
                .call(() -> question.sourceType != QuestionSource.EXAMPLE || question.vocabularyId == null
                        ? Uni.createFrom().voidItem()
                        : questions.hasExampleVocabularyLink(question.vocabularyId, question.exampleSentenceId)
                        .invoke(linked -> {
                            if (!linked) {
                                throw QuizQuestionRules.invalid(
                                        "vocabularyId", "Vocabulary must reference the example"
                                );
                            }
                        }).replaceWithVoid())
                .call(() -> validateReferences("JlptLevel", content.levelIds()))
                .call(() -> validateReferences("Lesson", content.lessonIds()))
                .call(() -> repository.hasDuplicate(question).invoke(duplicate -> {
                    if (duplicate) {
                        throw conflict("A question with this source and target already exists");
                    }
                }))
                .invoke(() -> question.contentKey = identity(question))
                .replaceWithVoid();
    }

    private Uni<Void> validateReferences(String entity, List<Long> ids) {
        return Multi.createFrom().iterable(ids).onItem().transformToUniAndConcatenate(id ->
                requireReference(entity, id)).collect().asList().replaceWithVoid();
    }

    private Uni<Void> requireReference(String entity, Long id) {
        return repository.referenceExists(entity, id).invoke(exists -> {
            if (!exists) {
                throw missing(entity);
            }
        }).replaceWithVoid();
    }

    private void validateIds(List<Long> ids, String field) {
        if (ids.stream().distinct().count() != ids.size()) {
            throw QuizQuestionRules.invalid(field, "IDs must be distinct");
        }
    }

    private void apply(QuizQuestion question, QuizQuestionContentRequest content) {
        question.sourceType = content.sourceType();
        question.exampleSentenceId = content.exampleId();
        question.vocabularyId = content.vocabularyId();
        question.sentenceReading = content.sentenceReading();
        question.targetStart = content.targetStart();
        question.targetLength = content.targetLength();
        question.targetReading = content.targetReading();
        question.explanationVi = content.explanationVi();
        question.explanationEn = content.explanationEn();
    }

    private QuizQuestionOption requireOption(List<QuizQuestionOption> values, Long id) {
        return values.stream().filter(option -> option.id.equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "QUIZ_OPTION_NOT_FOUND", "Option does not belong to this question")
                );
    }

    private List<QuestionOption> domainOptions(List<QuizQuestionOption> values) {
        return values.stream().map(
                option -> new QuestionOption(option.optionText, option.correct)
        ).toList();
    }

    private String identity(QuizQuestion question) {
        String source = question.sourceType == QuestionSource.EXAMPLE
                ? question.exampleSentenceId.toString() : question.sentenceReading;

        String identity = question.sourceType + "|" + source.length() + ":" + source
                + "|" + question.targetStart + "|" + question.targetLength;

        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(
                    "SHA-256 is required by the Java platform", impossible
            );
        }
    }

    private Uni<QuizQuestionResponse> complete(Uni<QuizQuestion> operation) {
        return operation.invoke(question -> question.updatedAt = LocalDateTime.now(ZoneOffset.UTC))
                .call(questions::flush).chain(question -> queries.detail(question.id))
                .onFailure(failure -> failure instanceof ConstraintViolationException
                        || failure instanceof OptimisticLockException
                        || failure instanceof PessimisticLockException
                        || failure instanceof LockAcquisitionException)
                .transform(failure -> conflict("Conflicting question write or reference change; reload and retry"));
    }

    private ConflictException conflict(String message) {
        return new ConflictException("QUIZ_QUESTION_CONFLICT", message);
    }

    private ResourceNotFoundException missing(String resource) {
        return new ResourceNotFoundException(
                "QUIZ_REFERENCE_NOT_FOUND",
                resource + " not found"
        );
    }
}
