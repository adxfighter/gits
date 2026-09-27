package ru.gits.task.bank.t08;

import java.util.Map;
import java.util.Objects;

/**
 * Credit decision and the bureau scores it is based on.
 */
public record Decision(Outcome outcome, Map<String, Integer> scores) {

    public enum Outcome {
        APPROVED,
        REJECTED,
        MANUAL_REVIEW
    }

    public Decision {
        Objects.requireNonNull(outcome, "outcome");
        scores = Map.copyOf(scores);
    }
}
