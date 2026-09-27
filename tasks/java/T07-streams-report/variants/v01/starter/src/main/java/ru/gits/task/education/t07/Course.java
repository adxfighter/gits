package ru.gits.task.education.t07;

import java.util.Objects;

/**
 * A course of the semester. The code identifies the course; titles may repeat.
 */
public record Course(String code, String title) {

    public Course {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(title, "title");
    }
}
