package ru.gits.task.medicine.t02;

import java.util.Objects;

/**
 * A hospitalised patient (anonymised: only the medical record number is kept).
 *
 * @param recordNo medical record number, unique in the hospital
 */
public record Patient(String recordNo) {

    public Patient {
        Objects.requireNonNull(recordNo, "recordNo");
    }
}
