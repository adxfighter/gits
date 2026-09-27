package ru.gits.task.telecom.t04;

import java.util.Optional;

/**
 * Billing account of the call being charged by the current thread. Shared by rating rules and the journal.
 */
public final class BillingContext {

    private static final ThreadLocal<String> ACCOUNT = new ThreadLocal<>();

    private BillingContext() {
    }

    public static Optional<String> currentAccount() {
        return Optional.ofNullable(ACCOUNT.get());
    }

    static void enter(String accountId) {
        ACCOUNT.set(accountId);
    }

    static void leave() {
        ACCOUNT.remove();
    }
}
