package ru.gits.api.session;

import java.util.UUID;

/** Published when a session is finished by the candidate or expires; indicators and the score are computed on it. */
public record SessionFinishedEvent(UUID sessionId) {
}
