package ru.gits.task.education.t06;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StudentIdVisibleTest {

    @Test
    void studentsWithTheSameRecordBookAreEqual() {
        assertThat(new StudentId("SGU", "2024-0157")).isEqualTo(new StudentId("SGU", "2024-0157"));
    }

    @Test
    void differentUniversitiesMeanDifferentStudents() {
        assertThat(new StudentId("SGU", "2024-0157")).isNotEqualTo(new StudentId("MGU", "2024-0157"));
    }
}
