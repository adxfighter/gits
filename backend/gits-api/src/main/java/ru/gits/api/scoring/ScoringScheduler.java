package ru.gits.api.scoring;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ru.gits.core.session.AssessmentSessionRepository;

/**
 * Scores finished sessions as soon as the runner has checked all their submits: after the finish the automatic
 * submits are still in the queue. Each session is scored in its own transaction; a failing one is logged and
 * retried on the next pass.
 */
@Component
@ConditionalOnProperty(name = "gits.scoring.scheduler", havingValue = "true", matchIfMissing = true)
class ScoringScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ScoringScheduler.class);
    private static final int BATCH = 20;

    private final AssessmentSessionRepository sessions;
    private final ScoringService scoring;

    ScoringScheduler(AssessmentSessionRepository sessions, ScoringService scoring) {
        this.sessions = sessions;
        this.scoring = scoring;
    }

    @Scheduled(fixedDelayString = "${gits.scoring.check-interval}")
    void scoreReadySessions() {
        for (UUID sessionId : sessions.findIdsReadyForScoring(BATCH)) {
            try {
                scoring.compute(sessionId);
            } catch (RuntimeException e) {
                LOG.error("Session {} could not be scored", sessionId, e);
            }
        }
    }
}
