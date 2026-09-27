package ru.gits.task.education.t06;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class StudentIdHiddenTest {

    private static List<StudentId> importFromSystem(int students) {
        List<StudentId> imported = new ArrayList<>();
        for (int i = 0; i < students; i++) {
            imported.add(new StudentId("SGU", "2024-" + i));
        }
        return imported;
    }

    @Test
    void importingTheSameStudentsTwiceDoesNotCreateDuplicates() {
        var group = new StudyGroup("ПМИ-101");

        group.addAll(importFromSystem(500));  // admissions office
        group.addAll(importFromSystem(500));  // timetable system

        assertThat(group.size()).isEqualTo(500);
    }

    @Test
    void studentCreatedElsewhereIsFound() {
        var group = new StudyGroup("ПМИ-101");
        group.addAll(importFromSystem(100));

        for (int i = 0; i < 100; i++) {
            assertThat(group.contains(new StudentId("SGU", "2024-" + i))).as("student %d", i).isTrue();
        }
    }

    @Test
    void studentCanBeRemovedByAnEqualId() {
        var group = new StudyGroup("ПМИ-101");
        group.add(new StudentId("SGU", "2024-7"));

        assertThat(group.remove(new StudentId("SGU", "2024-7"))).isTrue();
        assertThat(group.size()).isZero();
    }

    @Test
    void equalIdsHaveEqualHashCodes() {
        for (int i = 0; i < 1_000; i++) {
            var fromAdmissions = new StudentId("SGU", "N-" + i);
            var fromTimetable = new StudentId(new String("SGU"), new String("N-" + i));
            assertThat(fromAdmissions.hashCode()).isEqualTo(fromTimetable.hashCode());
        }
    }

    @Test
    void idWorksAsAMapKeyAndEqualityMeaningIsKept() {
        Map<StudentId, Integer> grades = new HashMap<>();
        grades.put(new StudentId("SGU", "2024-1"), 5);

        assertThat(grades.get(new StudentId("SGU", "2024-1"))).isEqualTo(5);
        assertThat(grades.get(new StudentId("SGU", "2024-2"))).isNull();
        assertThat(new StudentId("SGU", "A")).isNotEqualTo(new StudentId("SGU", "B")).isNotEqualTo(null);
    }
}
