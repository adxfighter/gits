package ru.gits.task.education.t01;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class AttendanceStatisticsVisibleTest {

    @Test
    void combinesSingleVisitsAndWholeLessons() {
        var statistics = new AttendanceStatistics();

        statistics.record(45);
        statistics.recordAll(List.of(30, 90, 15));

        assertThat(statistics.summary()).isEqualTo(new AttendanceSummary(4, 180, 90));
        assertThat(statistics.summary().averageMinutes()).isEqualTo(45.0);
    }

    @Test
    void rejectsImpossibleVisitLength() {
        var statistics = new AttendanceStatistics();

        assertThatThrownBy(() -> statistics.recordAll(List.of(30, 0))).isInstanceOf(IllegalArgumentException.class);
        assertThat(statistics.summary()).isEqualTo(AttendanceSummary.EMPTY);
    }
}
