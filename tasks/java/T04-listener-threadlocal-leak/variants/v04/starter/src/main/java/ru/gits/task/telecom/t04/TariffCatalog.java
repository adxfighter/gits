package ru.gits.task.telecom.t04;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Catalogue of tariffs; notifies listeners when a tariff changes. Shared by all billing threads.
 */
public final class TariffCatalog {

    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    public void addListener(Consumer<String> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(Consumer<String> listener) {
        listeners.remove(listener);
    }

    /** Announces that the tariff with the given code changed. */
    public void tariffChanged(String tariffCode) {
        for (Consumer<String> listener : listeners) {
            listener.accept(tariffCode);
        }
    }

    public int listenerCount() {
        return listeners.size();
    }
}
