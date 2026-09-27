package ru.gits.task.telecom.t05;

import java.util.List;
import java.util.Objects;

/**
 * Usage of one subscriber during a month.
 *
 * @param calls    calls in the order they were made
 * @param smsCount SMS sent
 */
public record MonthUsage(List<Call> calls, int smsCount) {

    public enum CallType {
        LOCAL,
        LONG_DISTANCE,
        ROAMING
    }

    /**
     * @param type    call type
     * @param seconds call duration, 0 for an unanswered call
     * @param weekend the call was made on Saturday or Sunday
     */
    public record Call(CallType type, int seconds, boolean weekend) {
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
