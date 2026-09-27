package ru.gits.task.education.t07;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;

class CourseReportHiddenTest {

    private static final Course CALCULUS_1 = new Course("MA-101", "Математический анализ");
    private static final Course CALCULUS_2 = new Course("MA-102", "Математический анализ");
    private static final Course PRACTICE = new Course("PR-201", "Производственная практика");

    @Test
    void coursesWithTheSameTitleAreReportedSeparately() {
        List<CourseLine> report = new CourseReport().build(List.of(CALCULUS_2, CALCULUS_1), List.of(
                new Grade("s1", CALCULUS_1, 5),
                new Grade("s2", CALCULUS_1, 5),
                new Grade("s3", CALCULUS_2, 3),
                new Grade("s4", CALCULUS_2, 2),
                new Grade("s5", CALCULUS_2, 4)));

        assertThat(report).containsExactly(
                new CourseLine("MA-101", "Математический анализ", 2, OptionalDouble.of(5.0)),
                new CourseLine("MA-102", "Математический анализ", 3, OptionalDouble.of(3.0)));
    }

    @Test
    void courseWithoutGradesHasAnEmptyAverage() {
        List<CourseLine> report = new CourseReport().build(List.of(CALCULUS_1, PRACTICE), List.of(
                new Grade("s1", CALCULUS_1, 4)));

        assertThat(report).containsExactly(
                new CourseLine("MA-101", "Математический анализ", 1, OptionalDouble.of(4.0)),
                new CourseLine("PR-201", "Производственная практика", 0, OptionalDouble.empty()));
    }

    @Test
    void noGradesAtAllGivesEmptyAverages() {
        List<CourseLine> report = new CourseReport().build(List.of(PRACTICE, CALCULUS_1), List.of());

        assertThat(report).extracting(CourseLine::code).containsExactly("MA-101", "PR-201");
        assertThat(report).allSatisfy(line -> {
            assertThat(line.gradeCount()).isZero();
            assertThat(line.average()).isEmpty();
        });
    }

    @Test
    void noCoursesGiveAnEmptyReport() {
        assertThat(new CourseReport().build(List.of(), List.of(new Grade("s1", CALCULUS_1, 4)))).isEmpty();
    }

    @Test
    void sameTitleCourseWithoutGradesDoesNotBorrowGrades() {
        List<CourseLine> report = new CourseReport().build(List.of(CALCULUS_1, CALCULUS_2), List.of(
                new Grade("s1", CALCULUS_1, 3)));

        assertThat(report).containsExactly(
                new CourseLine("MA-101", "Математический анализ", 1, OptionalDouble.of(3.0)),
                new CourseLine("MA-102", "Математический анализ", 0, OptionalDouble.empty()));
    }
}
