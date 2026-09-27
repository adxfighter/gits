package ru.gits.api.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Starts the calculation of indicators and the preliminary score after the session is committed. The calculation
 * itself arrives with P12; until then the trigger only records that the session is ready for it.
 */
@Component
class SessionScoringTrigger {

    private static final Logger LOG = LoggerFactory.getLogger(SessionScoringTrigger.class);

    @Async
    @TransactionalEventListener
    void onSessionFinished(SessionFinishedEvent event) {
        LOG.info("Session {} finished: indicators and score will be computed (P12)", event.sessionId());
    }
}
