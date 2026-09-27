package ru.gits.task.education.t00;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class GradeStatisticsVisibleTest {

    @Test
    void emptyGroupHasNoPassedStudents() {
        var statistics = new GradeStatistics(List.of(), 60);

        assertThat(statistics.passedCount()).isZero();
        assertThat(statistics.passRate()).isZero();
        assertThat(statistics.averagePoints()).isEmpty();
    }

    @Test
    void countsClearlyPassedAndFailedStudents() {
        var statistics = new GradeStatistics(
                List.of(new Grade("s1", 95), new Grade("s2", 30), new Grade("s3", 81)), 60);

        assertThat(statistics.passedCount()).isEqualTo(2);
        assertThat(statistics.bestPoints()).hasValue(95);
    }
}
