package ru.gits.task.education.t00;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GradeStatisticsHiddenTest {

    @Test
    void studentExactlyAtPassingPointsPassed() {
        var statistics = new GradeStatistics(List.of(new Grade("s1", 60)), 60);

        assertThat(statistics.passedCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"59,0", "60,1", "61,1"})
    void boundaryAroundPassingPoints(int points, long expectedPassed) {
        var statistics = new GradeStatistics(List.of(new Grade("s1", points)), 60);

        assertThat(statistics.passedCount()).isEqualTo(expectedPassed);
    }

    @Test
    void passRateCountsBoundaryStudents() {
        var statistics = new GradeStatistics(
                List.of(new Grade("s1", 60), new Grade("s2", 40), new Grade("s3", 75), new Grade("s4", 60)), 60);

        assertThat(statistics.passRate()).isCloseTo(0.75, within(1e-9));
    }

    @Test
    void zeroPassingPointsMeansEveryonePassed() {
        var statistics = new GradeStatistics(List.of(new Grade("s1", 0), new Grade("s2", 10)), 0);

        assertThat(statistics.passedCount()).isEqualTo(2);
    }

    @Test
    void otherStatisticsAreUnchanged() {
        var statistics = new GradeStatistics(List.of(new Grade("s1", 60), new Grade("s2", 90)), 60);

        assertThat(statistics.averagePoints()).hasValue(75.0);
        assertThat(statistics.bestPoints()).hasValue(90);
        assertThat(statistics.groupSize()).isEqualTo(2);
    }
}
