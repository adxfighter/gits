package ru.gits.task.education.t07;

import java.util.Objects;

/**
 * A grade from 2 to 5 given to a student for a course.
 */
public record Grade(String studentId, Course course, int score) {

    public Grade {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(course, "course");
        if (score < 2 || score > 5) {
            throw new IllegalArgumentException("score must be from 2 to 5: " + score);
        }
    }
}
