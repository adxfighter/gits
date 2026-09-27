package ru.gits.task.energy.t01;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ConsumptionAggregatorVisibleTest {

    @Test
    void sumsReadingsPerMeter() {
        var aggregator = new ConsumptionAggregator();

        aggregator.add(new MeterReading("M-1", 1_200, 1));
        aggregator.add(new MeterReading("M-1", 800, 2));
        aggregator.add(new MeterReading("M-2", 500, 1));

        assertThat(aggregator.totalFor("M-1")).isEqualTo(2_000);
        assertThat(aggregator.total()).isEqualTo(2_500);
        assertThat(aggregator.snapshot()).containsEntry("M-2", 500L).hasSize(2);
    }

    @Test
    void rejectsImplausibleReadings() {
        var aggregator = new ConsumptionAggregator();

        assertThatThrownBy(() -> aggregator.add(new MeterReading("M-1", -1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(aggregator.meterCount()).isZero();
    }
}
