package ru.gits.task.shop.t04;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PriceWidgetHiddenTest {

    @Test
    void disposedWidgetsAreReleasedByTheBoard() {
        var board = new PriceBoard();

        for (int card = 0; card < 5_000; card++) {
            new PriceWidget(board, "SKU-" + card, 100).dispose();
        }

        assertThat(board.listenerCount()).isZero();
    }

    @Test
    void disposedWidgetNoLongerReceivesUpdates() {
        var board = new PriceBoard();
        var widget = new PriceWidget(board, "SKU-1", 1_000);

        widget.dispose();
        board.publish("SKU-1", 1);

        assertThat(widget.displayedPrice()).isEqualTo(1_000);
        assertThat(widget.updates()).isZero();
    }

    @Test
    void onlyVisibleCardsStayRegistered() {
        var board = new PriceBoard();
        List<PriceWidget> visible = new ArrayList<>();
        for (int card = 0; card < 300; card++) {
            var widget = new PriceWidget(board, "SKU-" + card, 100);
            if (card % 3 == 0) {
                visible.add(widget);
            } else {
                widget.dispose();
            }
        }

        board.publish("SKU-0", 50);

        assertThat(board.listenerCount()).isEqualTo(visible.size());
        assertThat(visible.getFirst().displayedPrice()).isEqualTo(50);
    }

    @Test
    void disposeTwiceIsSafeAndLeavesOtherWidgetsAlone() {
        var board = new PriceBoard();
        var first = new PriceWidget(board, "SKU-1", 100);
        var second = new PriceWidget(board, "SKU-1", 100);

        first.dispose();
        first.dispose();
        board.publish("SKU-1", 70);

        assertThat(board.listenerCount()).isEqualTo(1);
        assertThat(second.displayedPrice()).isEqualTo(70);
        assertThat(first.displayedPrice()).isEqualTo(100);
    }

    @Test
    void manyWidgetsForOneProductEachGetOneUpdate() {
        var board = new PriceBoard();
        List<PriceWidget> widgets = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            widgets.add(new PriceWidget(board, "SKU-HOT", 100));
        }
        widgets.subList(0, 5).forEach(PriceWidget::dispose);

        board.publish("SKU-HOT", 99);

        assertThat(widgets.subList(0, 5)).allMatch(widget -> widget.updates() == 0);
        assertThat(widgets.subList(5, 10)).allMatch(widget -> widget.updates() == 1);
    }
}
