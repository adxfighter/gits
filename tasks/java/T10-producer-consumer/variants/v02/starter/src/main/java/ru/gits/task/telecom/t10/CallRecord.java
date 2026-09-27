package ru.gits.task.telecom.t10;

import java.util.Objects;

/**
 * Call detail record.
 */
public record CallRecord(String id, String caller, String callee, int durationSeconds) {

    public CallRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(callee, "callee");
    }
}
