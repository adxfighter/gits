package ru.gits.task.energy.t05;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Consumption during one hour.
 *
 * @param start          local start of the hour (minutes and seconds are zero)
 * @param kilowattHours  consumption during the hour, not negative
 */
public record HourlyReading(LocalDateTime start, int kilowattHours) {

    public HourlyReading {
        Objects.requireNonNull(start, "start");
        if (kilowattHours < 0 || start.getMinute() != 0 || start.getSecond() != 0) {
            throw new IllegalArgumentException("Invalid hourly reading at " + start);
        }
    }

    public LocalDateTime end() {
        return start.plusHours(1);
    }
}
