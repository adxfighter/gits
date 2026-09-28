package ru.gits.api.scoring;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ru.gits.core.session.AssessmentSessionRepository;

/**
 * Scores finished sessions as soon as the runner has checked all their submits: after the finish the automatic
 * submits are still in the queue. Each session is scored in its own transaction; a failing one is logged and
 * retried later with a growing delay, so it never holds back the others.
 */
@Component
@ConditionalOnProperty(name = "gits.scoring.scheduler", havingValue = "true", matchIfMissing = true)
class ScoringScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ScoringScheduler.class);
    private static final int BATCH = 100;
    private static final Duration MAX_DELAY = Duration.ofHours(1);

    private record Failure(int attempts, Instant retryAt) {
    }

    private final AssessmentSessionRepository sessions;
    private final ScoringService scoring;
    private final ScoringProperties properties;
    private final Clock clock;
    private final Map<UUID, Failure> failures = new ConcurrentHashMap<>();

    ScoringScheduler(AssessmentSessionRepository sessions, ScoringService scoring, ScoringProperties properties,
                     Clock clock) {
        this.sessions = sessions;
        this.scoring = scoring;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${gits.scoring.check-interval}")
    void scoreReadySessions() {
        Instant now = clock.instant();
        for (UUID sessionId : sessions.findIdsReadyForScoring(BATCH, now.minus(properties.stuckSubmitAfter()))) {
            Failure failure = failures.get(sessionId);
            if (failure != null && now.isBefore(failure.retryAt())) {
                continue;
            }
            try {
                scoring.compute(sessionId);
                failures.remove(sessionId);
            } catch (RuntimeException e) {
                int attempts = failure == null ? 1 : failure.attempts() + 1;
                Duration delay = properties.checkInterval().multipliedBy(1L << Math.min(attempts, 16));
                failures.put(sessionId, new Failure(attempts, now.plus(delay.compareTo(MAX_DELAY) > 0 ? MAX_DELAY
                        : delay)));
                LOG.error("Session {} could not be scored (attempt {})", sessionId, attempts, e);
            }
        }
    }
}
