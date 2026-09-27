package ru.gits.task.telecom.t01;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TrafficMeterVisibleTest {

    @Test
    void accountsPacketsFromOneGateway() {
        var meter = new TrafficMeter("79001234567");

        meter.addPacket(1_500);
        meter.addPacket(500);

        assertThat(meter.snapshot()).isEqualTo(new TrafficSnapshot(2, 2_000));
        assertThat(meter.snapshot().averagePacketBytes()).isEqualTo(1_000.0);
    }

    @Test
    void rejectsImpossiblePacketSize() {
        var meter = new TrafficMeter("79001234567");

        assertThatThrownBy(() -> meter.addPacket(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> meter.addPacket(TrafficMeter.MAX_PACKET_BYTES + 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(meter.snapshot()).isEqualTo(TrafficSnapshot.EMPTY);
    }
}
