package ru.gits.api.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import ru.gits.api.scoring.ScoringService;

/**
 * Scores the session right after it is committed as finished, if its runs are already checked. Usually the automatic
 * submits are still in the queue then; ScoringScheduler scores the session once they are done.
 */
@Component
class SessionScoringTrigger {

    private static final Logger LOG = LoggerFactory.getLogger(SessionScoringTrigger.class);

    private final ScoringService scoring;

    SessionScoringTrigger(ScoringService scoring) {
        this.scoring = scoring;
    }

    @Async
    @TransactionalEventListener
    void onSessionFinished(SessionFinishedEvent event) {
        try {
            var result = scoring.compute(event.sessionId());
            if (!result.scored()) {
                LOG.info("Session {} finished: scored once its submits are checked", event.sessionId());
            }
        } catch (RuntimeException e) {
            LOG.error("Session {} could not be scored now; the scheduler retries", event.sessionId(), e);
        }
    }
}
