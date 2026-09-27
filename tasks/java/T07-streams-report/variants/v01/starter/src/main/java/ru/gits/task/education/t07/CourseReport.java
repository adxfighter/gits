package ru.gits.task.education.t07;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/**
 * End-of-semester report: average grade per course.
 */
public final class CourseReport {

    /**
     * Builds one line per course, ordered by course code.
     *
     * @param courses courses of the semester
     * @param grades  grades given so far; grades of other courses are ignored
     */
    public List<CourseLine> build(List<Course> courses, List<Grade> grades) {
        Map<String, List<Grade>> gradesByCourse = grades.stream()
                .collect(Collectors.groupingBy(grade -> grade.course().title()));

        return courses.stream()
                .sorted(Comparator.comparing(Course::code))
                .map(course -> {
                    List<Grade> courseGrades = gradesByCourse.getOrDefault(course.title(), List.of());
                    double average = courseGrades.stream()
                            .mapToInt(Grade::score)
                            .average()
                            .getAsDouble();
                    return new CourseLine(course.code(), course.title(), courseGrades.size(), OptionalDouble.of(average));
                })
                .toList();
    }
}
