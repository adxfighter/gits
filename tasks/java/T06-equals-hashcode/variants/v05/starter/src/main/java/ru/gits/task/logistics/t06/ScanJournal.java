package ru.gits.task.logistics.t06;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Scan events grouped by parcel.
 */
public final class ScanJournal {

    private final Map<ParcelRef, List<String>> scans = new HashMap<>();

    /** Records that the parcel was scanned at the given hub. */
    public void record(ParcelRef parcel, String hub) {
        Objects.requireNonNull(parcel, "parcel");
        Objects.requireNonNull(hub, "hub");
        scans.computeIfAbsent(parcel, key -> new ArrayList<>()).add(hub);
    }

    /** Hubs where the parcel was scanned, in scan order. */
    public List<String> history(ParcelRef parcel) {
        return List.copyOf(scans.getOrDefault(parcel, List.of()));
    }

    public int parcelCount() {
        return scans.size();
    }
}
