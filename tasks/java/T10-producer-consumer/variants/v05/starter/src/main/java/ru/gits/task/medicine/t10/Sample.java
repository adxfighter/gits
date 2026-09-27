package ru.gits.task.medicine.t10;

import java.util.Objects;

/**
 * A patient's sample sent for analysis.
 */
public record Sample(String barcode, String patientId, String test) {

    public Sample {
        Objects.requireNonNull(barcode, "barcode");
        Objects.requireNonNull(patientId, "patientId");
        Objects.requireNonNull(test, "test");
    }
}
