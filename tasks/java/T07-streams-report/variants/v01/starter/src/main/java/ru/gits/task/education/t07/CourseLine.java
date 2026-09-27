package ru.gits.task.education.t07;

import java.util.OptionalDouble;

/**
 * One line of the course report. The average is empty when the course has no grades yet.
 */
public record CourseLine(String code, String title, int gradeCount, OptionalDouble average) {
}
