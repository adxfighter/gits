package ru.gits.task.education.t01;

import java.util.List;
import java.util.Objects;

/**
 * Attendance statistics of one course. Video conferencing servers call {@link #record(int)} and
 * {@link #recordAll(List)} concurrently; methodologists read {@link #summary()} at any time.
 */
public final class AttendanceStatistics {

    /** An online lesson cannot last longer than a working day. */
    public static final int MAX_VISIT_MINUTES = 8 * 60;

    private long visits;
    private long totalMinutes;
    private int maxMinutes;

    /** Records one visit. */
    public synchronized void record(int minutes) {
        validate(minutes);
        visits++;
        totalMinutes += minutes;
        maxMinutes = Math.max(maxMinutes, minutes);
    }

    /** Records all visits of a finished lesson. */
    public void recordAll(List<Integer> minutesPerVisit) {
        Objects.requireNonNull(minutesPerVisit, "minutesPerVisit");
        minutesPerVisit.forEach(AttendanceStatistics::validate);
        long lessonMinutes = 0;
        int lessonMax = 0;
        for (int minutes : minutesPerVisit) {
            lessonMinutes += minutes;
            lessonMax = Math.max(lessonMax, minutes);
        }
        visits += minutesPerVisit.size();
        totalMinutes += lessonMinutes;
        maxMinutes = Math.max(maxMinutes, lessonMax);
    }

    /** Visits, total and maximum taken from the same moment. */
    public AttendanceSummary summary() {
        return new AttendanceSummary(visits, totalMinutes, maxMinutes);
    }

    private static void validate(int minutes) {
        if (minutes <= 0 || minutes > MAX_VISIT_MINUTES) {
            throw new IllegalArgumentException("Impossible visit length: " + minutes + " min");
        }
    }
}
