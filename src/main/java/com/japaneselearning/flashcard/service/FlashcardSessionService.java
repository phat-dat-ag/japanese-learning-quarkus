package com.japaneselearning.flashcard.service;

import com.japaneselearning.common.logging.StructuredLog;
import com.japaneselearning.common.logging.LogOperation;
import com.japaneselearning.common.logging.OperationLog;
import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.flashcard.domain.FlashcardSessionStatus;
import com.japaneselearning.flashcard.domain.ProgressState;
import com.japaneselearning.flashcard.dto.FlashcardPageResponse;
import com.japaneselearning.flashcard.dto.FlashcardSessionCreateRequest;
import com.japaneselearning.flashcard.dto.FlashcardSessionCreatedResponse;
import com.japaneselearning.flashcard.dto.FlashcardSessionResponse;
import com.japaneselearning.flashcard.entity.FlashcardSession;
import com.japaneselearning.flashcard.entity.FlashcardSessionItem;
import com.japaneselearning.flashcard.repository.FlashcardLearningRepository;
import com.japaneselearning.flashcard.repository.FlashcardSessionRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.panache.common.Page;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class FlashcardSessionService {
    private static final Logger LOG = Logger.getLogger(FlashcardSessionService.class);

    private final FlashcardSessionRepository sessions;
    private final FlashcardLearningRepository catalogue;
    private final VocabularyProgressService progress;
    private final FlashcardTime time;

    public FlashcardSessionService(
            FlashcardSessionRepository sessions,
            FlashcardLearningRepository catalogue,
            VocabularyProgressService progress,
            FlashcardTime time
    ) {
        this.sessions = sessions;
        this.catalogue = catalogue;
        this.progress = progress;
        this.time = time;
    }

    @WithSession
    @LogOperation(value = "flashcard.session.creation")
    public Uni<FlashcardSessionCreatedResponse> create(
            String subject,
            FlashcardSessionCreateRequest request
    ) {
        return Panache.withTransaction(() -> {
                    Instant now = time.now();

                    return catalogue.validateScope(null, request.lessonId())
                            .chain(() -> catalogue.select(
                                    subject,
                                    request.lessonId(),
                                    request.selectedMode(),
                                    FlashcardTime.stored(now),
                                    request.selectedLimit(),
                                    request.shuffle()
                            ))
                            .chain(ids -> {
                                StructuredLog.event(
                                        LOG,
                                        Logger.Level.DEBUG,
                                        "flashcard.session.selection",
                                        Map.of(
                                                "reason", request.selectedMode().name(),
                                                "count", ids.size(),
                                                "total", request.selectedLimit()
                                        )
                                );
                                if (ids.isEmpty()) {
                                    return Uni.createFrom().item(
                                            new FlashcardSessionCreatedResponse("NO_ELIGIBLE_CARDS", 0, null)
                                    );
                                }

                                return persist(subject, request, ids, now)
                                        .chain(session -> detail(subject, session))
                                        .chain(response -> OperationLog.outcome(
                                                Uni.createFrom().item(new FlashcardSessionCreatedResponse(
                                                        "CREATED", ids.size(), response
                                                )),
                                                "completed",
                                                Map.of(
                                                        "sessionId", response.sessionId(),
                                                        "count", ids.size()
                                                )
                                        ));
                            });
                }).onFailure(FlashcardConflicts::concurrent)
                .transform(failure -> FlashcardConflicts.changed());
    }

    private Uni<FlashcardSession> persist(
            String subject,
            FlashcardSessionCreateRequest request,
            List<Long> ids,
            Instant now
    ) {
        List<Long> order = new ArrayList<>(ids);

        if (request.shuffle() && request.selectedMode().scheduling())
            Collections.shuffle(order);

        FlashcardSession session = new FlashcardSession();

        session.userSubject = subject;
        session.lessonId = request.lessonId();
        session.mode = request.selectedMode();
        session.status = FlashcardSessionStatus.IN_PROGRESS;
        session.itemCount = order.size();
        session.createdAt = FlashcardTime.stored(now);
        session.updatedAt = session.createdAt;

        return sessions.persistAndFlush(session).chain(() -> {
            List<FlashcardSessionItem> items = new ArrayList<>();

            for (int i = 0; i < order.size(); i++) {
                FlashcardSessionItem item = new FlashcardSessionItem();

                item.sessionId = session.id;
                item.vocabularyId = order.get(i);
                item.position = i;
                items.add(item);
            }

            return sessions.persistItems(items)
                    .call(sessions::flush)
                    .replaceWith(session);

        });
    }

    @WithSession
    public Uni<FlashcardSessionResponse> get(String subject, Long id) {
        return Panache.withTransaction(() -> owned(subject, id, false)
                .chain(session -> detail(subject, session))
        );
    }

    @WithSession
    public Uni<FlashcardPageResponse<FlashcardSessionResponse>> list(
            String subject,
            Long lessonId,
            FlashcardSessionStatus status,
            int page,
            int size
    ) {
        FlashcardValidation.pagination(page, size);

        var parameters = new HashMap<String, Object>();

        parameters.put("subject", subject);
        String condition = "userSubject = :subject";

        if (lessonId != null) {
            condition += " and lessonId = :lesson";
            parameters.put("lesson", lessonId);
        }

        if (status != null) {
            condition += " and status = :status";
            parameters.put("status", status);
        }

        String filter = condition;

        return Panache.withTransaction(() -> catalogue.validateScope(null, lessonId)
                .chain(() -> sessions.find(filter + " order by createdAt desc, id desc", parameters)
                        .page(Page.of(page, size))
                        .list()
                )
                .chain(rows -> sessions.count(filter, parameters)
                        .map(total -> FlashcardPageResponse.of(
                                        rows.stream().map(row -> response(row, null)).toList(),
                                        page,
                                        size,
                                        total
                                )
                        )
                )
        );
    }

    @WithSession
    @LogOperation(value = "flashcard.session.finish", id = "sessionId")
    public Uni<FlashcardSessionResponse> finish(String subject, Long id) {
        return Panache.withTransaction(() -> owned(subject, id, true).invoke(session -> {
                            if (session.status != FlashcardSessionStatus.COMPLETED)
                                throw FlashcardConflicts.changed();
                        }).chain(session -> detail(subject, session))
                )
                .onFailure(FlashcardConflicts::concurrent)
                .transform(failure -> FlashcardConflicts.changed());
    }

    @WithSession
    @LogOperation(value = "flashcard.session.abandon", id = "sessionId")
    public Uni<FlashcardSessionResponse> abandon(String subject, Long id) {
        return Panache.withTransaction(() -> owned(subject, id, true).invoke(session -> {
                    if (session.status == FlashcardSessionStatus.IN_PROGRESS) {
                        session.status = FlashcardSessionStatus.ABANDONED;
                        session.abandonedAt = FlashcardTime.stored(time.now());
                        session.updatedAt = session.abandonedAt;
                    }
                }).chain(session -> detail(subject, session)))
                .onFailure(FlashcardConflicts::concurrent)
                .transform(failure -> FlashcardConflicts.changed());
    }

    Uni<FlashcardSession> owned(String subject, Long id, boolean lock) {
        return sessions.owned(subject, id, lock).onItem().ifNull()
                .failWith(() -> new ResourceNotFoundException(
                        "FLASHCARD_SESSION_NOT_FOUND",
                        "Flashcard session not found"
                ));
    }

    private Uni<FlashcardSessionResponse> detail(String subject, FlashcardSession session) {
        return sessions.items(session.id).chain(items -> progress.states(
                                subject,
                                items.stream().map(item -> item.vocabularyId).toList()
                        )
                        .map(states -> response(session, items.stream().map(
                                item -> new FlashcardSessionResponse.Item(
                                        item.id,
                                        item.vocabularyId,
                                        item.position,
                                        FlashcardTime.exposed(item.completedAt),
                                        states.getOrDefault(item.vocabularyId, ProgressState.unseen())
                                )
                        ).toList()))
        );
    }

    private FlashcardSessionResponse response(
            FlashcardSession session,
            List<FlashcardSessionResponse.Item> items
    ) {
        return new FlashcardSessionResponse(
                session.id,
                session.lessonId,
                session.mode,
                session.status,
                session.itemCount,
                session.completedCount,
                session.completedCount,
                FlashcardTime.exposed(session.createdAt),
                FlashcardTime.exposed(session.completedAt),
                items,
                FlashcardTime.exposed(session.abandonedAt)
        );
    }
}
