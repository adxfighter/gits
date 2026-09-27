package ru.gits.api.session;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every {@code gits.session.expiry-check} submits the tasks of sessions whose time is over. */
@Component
@ConditionalOnProperty(name = "gits.session.expiry-scheduler", havingValue = "true", matchIfMissing = true)
class SessionExpiryScheduler {

    private final SessionService sessions;

    SessionExpiryScheduler(SessionService sessions) {
        this.sessions = sessions;
    }

    @Scheduled(fixedDelayString = "${gits.session.expiry-check}", initialDelayString = "${gits.session.expiry-check}")
    void expireOverdueSessions() {
        sessions.expireOverdue();
    }
}
