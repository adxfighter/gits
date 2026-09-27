package ru.gits.task.medicine.t08;

import java.util.Map;

/**
 * Results by laboratory name and, for laboratories without a result, the reason by laboratory name.
 */
public record LabReport(Map<String, LabResult> results, Map<String, String> failures) {

    public LabReport {
        results = Map.copyOf(results);
        failures = Map.copyOf(failures);
    }

    /** True when every laboratory answered. */
    public boolean isComplete() {
        return failures.isEmpty();
    }
}
