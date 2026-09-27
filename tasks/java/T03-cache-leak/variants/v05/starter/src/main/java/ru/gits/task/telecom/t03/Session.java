package ru.gits.task.telecom.t03;

import java.util.Objects;

/**
 * Mobile data session of a subscriber.
 *
 * @param sessionId      session identifier assigned by the packet core
 * @param msisdn         subscriber phone number
 * @param speedLimitKbps tariff speed limit
 */
public record Session(String sessionId, String msisdn, int speedLimitKbps) {

    public Session {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(msisdn, "msisdn");
    }
}
