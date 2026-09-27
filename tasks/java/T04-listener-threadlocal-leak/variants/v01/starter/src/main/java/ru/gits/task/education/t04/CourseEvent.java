package ru.gits.task.education.t04;

import java.util.Objects;

/**
 * Something that happened in a course.
 *
 * @param courseId course the event belongs to
 * @param type     kind of event
 * @param details  human-readable description
 */
public record CourseEvent(String courseId, Type type, String details) {

    public enum Type {
        LESSON_PUBLISHED,
        SCHEDULE_CHANGED,
        GRADES_PUBLISHED
    }

    public CourseEvent {
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(type, "type");
    }
}
