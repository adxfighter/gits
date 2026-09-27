package ru.gits.task.telecom.t04;

import java.util.Objects;

/**
 * A completed call to be charged.
 *
 * @param callId    call identifier from the switch
 * @param accountId subscriber's billing account
 * @param callee    called number
 * @param seconds   call duration
 */
public record CallRecord(String callId, String accountId, String callee, int seconds) {

    public CallRecord {
        Objects.requireNonNull(callId, "callId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(callee, "callee");
        if (seconds < 0) {
            throw new IllegalArgumentException("Negative call duration");
        }
    }
}
