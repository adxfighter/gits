package ru.gits.task.telecom.t08;

import java.util.Objects;

/**
 * Result of the number check.
 */
public record Verdict(String msisdn, String operatorId, boolean flagged) {

    public Verdict {
        Objects.requireNonNull(msisdn, "msisdn");
        Objects.requireNonNull(operatorId, "operatorId");
    }
}
