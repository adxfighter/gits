package ru.gits.api.session;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Assessment session limits bound from {@code gits.session.*}.
 *
 * @param timeLimit        length of a session, from start to automatic submission
 * @param runsPerTask      RUN launches allowed per task (SUBMIT is not counted)
 * @param autosaveInterval minimum time between two code snapshots of a task
 * @param maxCodeBytes     size limit of a code snapshot (UTF-8, all editable files together)
 * @param recentSessions   how many latest sessions of the company are avoided when choosing variants
 * @param expiryCheck      how often expired sessions are submitted automatically
 */
@ConfigurationProperties(prefix = "gits.session")
public record SessionProperties(Duration timeLimit, int runsPerTask, Duration autosaveInterval, int maxCodeBytes,
                                int recentSessions, Duration expiryCheck) {
}
