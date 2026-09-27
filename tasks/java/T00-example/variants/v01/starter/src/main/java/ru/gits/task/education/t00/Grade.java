package ru.gits.task.education.t00;

import java.util.Objects;

/**
 * Exam result of one student.
 *
 * @param studentId student identifier in the dean's office system
 * @param points    exam points, 0..100
 */
public record Grade(String studentId, int points) {

    public static final int MIN_POINTS = 0;
    public static final int MAX_POINTS = 100;

    public Grade {
        Objects.requireNonNull(studentId, "studentId");
        if (points < MIN_POINTS || points > MAX_POINTS) {
            throw new IllegalArgumentException("points must be within 0..100: " + points);
        }
    }
}
