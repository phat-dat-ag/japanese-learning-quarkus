package com.japaneselearning.security;

import com.japaneselearning.flashcard.entity.VocabularyProgress;
import com.japaneselearning.flashcard.repository.VocabularyProgressRepository;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

@Alternative
@ApplicationScoped
public class CoordinatedFlashcardProgressRepository extends VocabularyProgressRepository {
    private volatile ReadBarrier barrier;

    public ReadBarrier coordinate(String subject) {
        var next = new ReadBarrier(subject);
        barrier = next;
        return next;
    }

    public void reset() {
        var previous = barrier;
        barrier = null;
        if (previous != null) previous.release.complete(null);
    }

    @Override
    public Uni<VocabularyProgress> findOwned(String subject, Long vocabularyId) {
        return super.findOwned(subject, vocabularyId).call(row -> {
            var active = barrier;

            if (active == null || !active.subject.equals(subject))
                return Uni.createFrom().voidItem();

            var context = Vertx.currentContext();

            active.versions.add(row == null ? -1L : row.version);

            if (active.versions.size() == 2)
                active.release.complete(null);

            return Uni.createFrom().completionStage(active.release)
                    .emitOn(command -> context.runOnContext(ignored -> command.run()))
                    .ifNoItem().after(Duration.ofSeconds(10)).fail();
        });
    }

    public static final class ReadBarrier {
        private final String subject;
        private final CompletableFuture<Void> release = new CompletableFuture<>();
        private final List<Long> versions = new CopyOnWriteArrayList<>();

        private ReadBarrier(String subject) {
            this.subject = subject;
        }

        public List<Long> observedVersions() {
            return List.copyOf(versions);
        }
    }
}