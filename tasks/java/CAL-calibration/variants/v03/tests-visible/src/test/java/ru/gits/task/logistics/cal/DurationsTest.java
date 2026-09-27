package ru.gits.task.logistics.cal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DurationsTest {

    @Test
    void hoursAndMinutes() {
        assertThat(Durations.toMinutes("1h 30m")).isEqualTo(90);
    }

    @Test
    void onlyHoursOrOnlyMinutes() {
        assertThat(Durations.toMinutes("2h")).isEqualTo(120);
        assertThat(Durations.toMinutes("45m")).isEqualTo(45);
    }

    @Test
    void textThatIsNotADurationIsRejected() {
        assertThatThrownBy(() -> Durations.toMinutes("полчаса")).isInstanceOf(IllegalArgumentException.class);
    }
}
