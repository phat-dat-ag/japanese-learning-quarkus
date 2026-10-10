package com.japaneselearning.quiz.player.service;

import com.japaneselearning.quiz.player.dto.QuizProgressBreakdownResponse;
import com.japaneselearning.quiz.player.dto.QuizProgressBreakdownResponse.Bucket;
import com.japaneselearning.quiz.player.dto.QuizProgressResponse;
import com.japaneselearning.quiz.repository.QuizProgressRepository;
import com.japaneselearning.quiz.repository.QuizProgressRepository.BreakdownCounts;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@ApplicationScoped
public class QuizProgressService {
    private final QuizProgressRepository progress;

    public QuizProgressService(QuizProgressRepository progress) {
        this.progress = progress;
    }

    @WithTransaction
    public Uni<QuizProgressResponse> overall(String subject) {
        return progress.overall(subject)
                .map(counts -> new QuizProgressResponse(
                        counts.completedSessions(),
                        counts.answeredCount(),
                        counts.correctCount(),
                        counts.answeredCount() - counts.correctCount(),
                        ratio(counts.correctCount(), counts.answeredCount(), 100),
                        counts.bestScore(),
                        ratio(counts.correctCount(), counts.completedSessions(), 1),
                        counts.latestCompletedAt()
                ));
    }

    @WithTransaction
    public Uni<QuizProgressBreakdownResponse> breakdown(String subject) {
        return progress.levels(subject)
                .chain(levels -> progress.lessons(subject)
                        .map(lessons -> new QuizProgressBreakdownResponse(
                                buckets(levels),
                                buckets(lessons)
                        ))
                );
    }

    private List<Bucket> buckets(List<BreakdownCounts> counts) {
        return counts.stream()
                .map(count -> new Bucket(
                        count.classification(),
                        count.levelId(),
                        count.lessonId(),
                        count.completedSessions(),
                        count.answeredCount(),
                        count.correctCount(),
                        count.answeredCount() - count.correctCount(),
                        ratio(count.correctCount(), count.answeredCount(), 100)
                ))
                .toList();
    }

    private BigDecimal ratio(long numerator, long denominator, int multiplier) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(2);
        }

        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(multiplier))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}
