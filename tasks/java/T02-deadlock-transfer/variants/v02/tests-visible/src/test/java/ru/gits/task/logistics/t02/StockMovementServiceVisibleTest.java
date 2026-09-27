package ru.gits.task.logistics.t02;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StockMovementServiceVisibleTest {

    private final StockMovementService service = new StockMovementService();

    @Test
    void movesABatchBetweenWarehouses() {
        var moscow = new Warehouse("MSK");
        var kazan = new Warehouse("KZN");
        moscow.receive("PALLET-1", 10);

        service.move(moscow, kazan, "PALLET-1", 4);

        assertThat(moscow.quantity("PALLET-1")).isEqualTo(6);
        assertThat(kazan.quantity("PALLET-1")).isEqualTo(4);
    }

    @Test
    void rebalanceMovesHalfOfTheDifference() {
        var moscow = new Warehouse("MSK");
        var kazan = new Warehouse("KZN");
        moscow.receive("BOX", 11);
        kazan.receive("BOX", 2);

        int moved = service.rebalance(kazan, moscow, "BOX");

        assertThat(moved).isEqualTo(4);
        assertThat(moscow.quantity("BOX")).isEqualTo(7);
        assertThat(kazan.quantity("BOX")).isEqualTo(6);
    }
}
