package ru.gits.task.energy.t10;

import java.time.Instant;
import java.util.Objects;

/**
 * A meter reading.
 */
public record Reading(String meterId, Instant at, long wh) {

    public Reading {
        Objects.requireNonNull(meterId, "meterId");
        Objects.requireNonNull(at, "at");
    }
}
