package ru.gits.task.education.t01;

/**
 * Attendance statistics of a course at one moment.
 *
 * @param visits       number of recorded visits
 * @param totalMinutes total minutes of all visits
 * @param maxMinutes   longest visit, 0 when there were no visits
 */
public record AttendanceSummary(long visits, long totalMinutes, int maxMinutes) {

    public static final AttendanceSummary EMPTY = new AttendanceSummary(0, 0, 0);

    /** Average visit length in minutes, 0 when there were no visits. */
    public double averageMinutes() {
        return visits == 0 ? 0 : (double) totalMinutes / visits;
    }
}
