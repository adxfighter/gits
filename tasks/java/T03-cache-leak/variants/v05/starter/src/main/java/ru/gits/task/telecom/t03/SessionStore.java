package ru.gits.task.telecom.t03;

import java.util.Optional;

/**
 * Distributed session store of the packet core. Remote and comparatively slow.
 */
@FunctionalInterface
public interface SessionStore {

    /** The session, or empty when it has ended. */
    Optional<Session> find(String sessionId);
}
