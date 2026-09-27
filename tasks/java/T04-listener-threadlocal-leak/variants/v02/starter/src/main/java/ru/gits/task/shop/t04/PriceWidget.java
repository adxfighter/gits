package ru.gits.task.shop.t04;

import java.util.Objects;

/**
 * Product card on the storefront. Shows the current price and follows price changes until disposed.
 */
public final class PriceWidget {

    private final PriceBoard board;
    private final String sku;
    private long displayedPriceKopecks;
    private int updates;
    private boolean disposed;

    public PriceWidget(PriceBoard board, String sku, long initialPriceKopecks) {
        this.board = Objects.requireNonNull(board, "board");
        this.sku = Objects.requireNonNull(sku, "sku");
        this.displayedPriceKopecks = initialPriceKopecks;
        board.addListener(this::onPrice);
    }

    private void onPrice(String changedSku, long priceKopecks) {
        if (sku.equals(changedSku)) {
            displayedPriceKopecks = priceKopecks;
            updates++;
        }
    }

    /** Stops following price changes; called when the card leaves the screen. */
    public void dispose() {
        if (!disposed) {
            disposed = true;
            board.removeListener(this::onPrice);
        }
    }

    public long displayedPrice() {
        return displayedPriceKopecks;
    }

    /** How many price changes for this product the widget has applied. */
    public int updates() {
        return updates;
    }

    public String sku() {
        return sku;
    }
}
