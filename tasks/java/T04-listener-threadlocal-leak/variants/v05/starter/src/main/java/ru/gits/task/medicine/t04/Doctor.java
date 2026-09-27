package ru.gits.task.medicine.t04;

import java.util.Objects;

/**
 * A physician acting in the medical information system.
 *
 * @param employeeId personnel number
 * @param specialty  medical specialty
 */
public record Doctor(String employeeId, String specialty) {

    public Doctor {
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(specialty, "specialty");
    }
}
