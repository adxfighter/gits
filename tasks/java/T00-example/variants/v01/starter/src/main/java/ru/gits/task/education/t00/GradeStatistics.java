package ru.gits.task.education.t00;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Exam statistics for one group of students.
 */
public final class GradeStatistics {

    private final List<Grade> grades;
    private final int passingPoints;

    /**
     * @param grades        exam results of the group
     * @param passingPoints the exam is passed with at least this many points
     */
    public GradeStatistics(List<Grade> grades, int passingPoints) {
        this.grades = List.copyOf(Objects.requireNonNull(grades, "grades"));
        if (passingPoints < Grade.MIN_POINTS || passingPoints > Grade.MAX_POINTS) {
            throw new IllegalArgumentException("passingPoints must be within 0..100: " + passingPoints);
        }
        this.passingPoints = passingPoints;
    }

    /** Average points of the group, empty when there are no results. */
    public OptionalDouble averagePoints() {
        return grades.stream().mapToInt(Grade::points).average();
    }

    /** Best result in the group, empty when there are no results. */
    public OptionalInt bestPoints() {
        return grades.stream().mapToInt(Grade::points).max();
    }

    /** Number of students who passed the exam. */
    public long passedCount() {
        return grades.stream()
                .filter(grade -> grade.points() > passingPoints)
                .count();
    }

    /** Share of students who passed, from 0.0 to 1.0; 0.0 for an empty group. */
    public double passRate() {
        if (grades.isEmpty()) {
            return 0.0;
        }
        return (double) passedCount() / grades.size();
    }

    public int groupSize() {
        return grades.size();
    }
}
