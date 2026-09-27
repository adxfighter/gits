package ru.gits.task.bank.t04;

import java.util.Optional;

/**
 * Client of the request being processed by the current thread. Shared infrastructure, used by all
 * request handlers and the audit log.
 */
public final class SecurityContext {

    private static final ThreadLocal<String> CURRENT_CLIENT = new ThreadLocal<>();

    private SecurityContext() {
    }

    public static Optional<String> currentClient() {
        return Optional.ofNullable(CURRENT_CLIENT.get());
    }

    static void setClient(String clientId) {
        CURRENT_CLIENT.set(clientId);
    }

    static void clear() {
        CURRENT_CLIENT.remove();
    }
}
