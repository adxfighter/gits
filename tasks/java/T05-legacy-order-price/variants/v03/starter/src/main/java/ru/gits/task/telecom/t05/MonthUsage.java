package ru.gits.task.telecom.t05;

import java.util.List;
import java.util.Objects;

/**
 * Usage of one subscriber during a month.
 *
 * @param calls      calls made
 * @param smsCount   SMS sent
 * @param pensioner  the subscriber has the pensioner benefit
 */
public record MonthUsage(List<Call> calls, int smsCount, boolean pensioner) {

    public enum CallType {
        LOCAL,
        LONG_DISTANCE
    }

    /**
     * @param type    call type
     * @param seconds call duration, 0 for an unanswered call
     */
    public record Call(CallType type, int seconds) {
        public Call {
            Objects.requireNonNull(type, "type");
            if (seconds < 0) {
                throw new IllegalArgumentException("Negative call duration");
            }
        }
    }

    public MonthUsage {
        calls = List.copyOf(calls);
        if (smsCount < 0) {
            throw new IllegalArgumentException("Negative SMS count");
        }
    }
}
