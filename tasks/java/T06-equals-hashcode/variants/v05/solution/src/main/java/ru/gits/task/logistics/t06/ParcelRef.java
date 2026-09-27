package ru.gits.task.logistics.t06;

import java.util.Locale;
import java.util.Objects;

/**
 * Reference to a parcel scanned at sorting hubs.
 */
public final class ParcelRef {

    private final String carrier;
    private final String trackingNo;
    private final String originHub;
    private final int labelVersion;

    public ParcelRef(String carrier, String trackingNo, String originHub, int labelVersion) {
        this.carrier = Objects.requireNonNull(carrier, "carrier");
        this.trackingNo = Objects.requireNonNull(trackingNo, "trackingNo");
        this.originHub = Objects.requireNonNull(originHub, "originHub");
        if (labelVersion < 1) {
            throw new IllegalArgumentException("labelVersion must be positive: " + labelVersion);
        }
        this.labelVersion = labelVersion;
    }

    public String carrier() {
        return carrier;
    }

    public String trackingNo() {
        return trackingNo;
    }

    public String originHub() {
        return originHub;
    }

    public int labelVersion() {
        return labelVersion;
    }

    /** The same parcel with a reprinted label. */
    public ParcelRef reprinted() {
        return new ParcelRef(carrier, trackingNo, originHub, labelVersion + 1);
    }

    /** Tracking number normalised the same way for equals and hashCode: case is ignored. */
    private String trackingKey() {
        return trackingNo.toUpperCase(Locale.ROOT);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ParcelRef that)) {
            return false;
        }
        return carrier.equals(that.carrier)
                && trackingKey().equals(that.trackingKey())
                && originHub.equals(that.originHub);
    }

    @Override
    public int hashCode() {
        // Only the fields of equals; the label version is left out.
        return Objects.hash(carrier, trackingKey(), originHub);
    }

    @Override
    public String toString() {
        return carrier + ":" + trackingNo + "@" + originHub + " v" + labelVersion;
    }
}
