package ru.gits.task.energy.t02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class FlowDispatcherVisibleTest {

    private final FlowDispatcher dispatcher = new FlowDispatcher();

    @Test
    void appliesABatchOfFlows() {
        var north = new GridNode(1, 100, 80);
        var south = new GridNode(2, 100, 20);
        var east = new GridNode(3, 100, 50);

        int shifted = dispatcher.applyBatch(List.of(new Flow(north, south, 30), new Flow(east, south, 10)));

        assertThat(shifted).isEqualTo(40);
        assertThat(List.of(north.load(), south.load(), east.load())).containsExactly(50, 60, 40);
    }

    @Test
    void rejectedBatchChangesNothing() {
        var north = new GridNode(1, 100, 80);
        var south = new GridNode(2, 60, 50);

        assertThatThrownBy(() -> dispatcher.applyBatch(List.of(new Flow(north, south, 5), new Flow(north, south, 10))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(north.load()).isEqualTo(80);
        assertThat(south.load()).isEqualTo(50);
    }
}
