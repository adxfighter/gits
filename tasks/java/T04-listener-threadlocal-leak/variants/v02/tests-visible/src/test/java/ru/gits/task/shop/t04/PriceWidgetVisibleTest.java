package ru.gits.task.shop.t04;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PriceWidgetVisibleTest {

    @Test
    void followsPriceChangesOfItsProduct() {
        var board = new PriceBoard();
        var widget = new PriceWidget(board, "SKU-1", 1_000);

        board.publish("SKU-1", 900);
        board.publish("SKU-2", 5);

        assertThat(widget.displayedPrice()).isEqualTo(900);
        assertThat(widget.updates()).isEqualTo(1);
    }

    @Test
    void newWidgetRegistersWithTheBoard() {
        var board = new PriceBoard();

        new PriceWidget(board, "SKU-1", 1_000);

        assertThat(board.listenerCount()).isEqualTo(1);
    }
}
