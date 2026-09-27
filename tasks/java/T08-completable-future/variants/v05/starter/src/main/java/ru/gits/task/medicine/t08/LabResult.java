package ru.gits.task.medicine.t08;

import java.util.Objects;

/**
 * Result of one analysis, e.g. "HGB" = "132 g/L".
 */
public record LabResult(String analysis, String value) {

    public LabResult {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(value, "value");
    }
}
