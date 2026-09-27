package ru.gits.task.education.t07;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;

class CourseReportVisibleTest {

    private static final Course ALGEBRA = new Course("AL-101", "Алгебра");
    private static final Course HISTORY = new Course("HI-101", "История");

    @Test
    void averageIsComputedPerCourse() {
        List<CourseLine> report = new CourseReport().build(List.of(HISTORY, ALGEBRA), List.of(
                new Grade("s1", ALGEBRA, 5),
                new Grade("s2", ALGEBRA, 4),
                new Grade("s1", HISTORY, 3)));

        assertThat(report).containsExactly(
                new CourseLine("AL-101", "Алгебра", 2, OptionalDouble.of(4.5)),
                new CourseLine("HI-101", "История", 1, OptionalDouble.of(3.0)));
    }

    @Test
    void gradesOfOtherCoursesAreIgnored() {
        var physics = new Course("PH-101", "Физика");
        List<CourseLine> report = new CourseReport().build(List.of(ALGEBRA), List.of(
                new Grade("s1", ALGEBRA, 4),
                new Grade("s1", physics, 2)));

        assertThat(report).containsExactly(new CourseLine("AL-101", "Алгебра", 1, OptionalDouble.of(4.0)));
    }
}
