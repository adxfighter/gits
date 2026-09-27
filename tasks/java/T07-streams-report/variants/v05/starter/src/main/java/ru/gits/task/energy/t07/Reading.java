package ru.gits.task.energy.t07;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Net consumption of one metering interval, Wh. Negative when the house exports energy to the grid.
 */
public record Reading(String meterId, LocalDate day, long wh) {

    public Reading {
        Objects.requireNonNull(meterId, "meterId");
        Objects.requireNonNull(day, "day");
    }
}
