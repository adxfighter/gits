package ru.gits.task.shop.t04;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared board of current prices; notifies listeners about changes. Owned by another team.
 */
public final class PriceBoard {

    private final List<PriceListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(PriceListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Removes a previously added listener.
     *
     * @return whether the listener was registered
     */
    public boolean removeListener(PriceListener listener) {
        return listeners.remove(listener);
    }

    public void publish(String sku, long priceKopecks) {
        for (PriceListener listener : listeners) {
            listener.priceChanged(sku, priceKopecks);
        }
    }

    public int listenerCount() {
        return listeners.size();
    }
}
