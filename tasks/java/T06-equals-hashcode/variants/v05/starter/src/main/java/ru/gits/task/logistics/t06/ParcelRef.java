package ru.gits.task.logistics.t06;

import java.util.Objects;

/**
 * Reference to a parcel. A reprinted label keeps the parcel the same, so the label version
 * does not take part in equality.
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ParcelRef that)) {
            return false;
        }
        return carrier.equals(that.carrier)
                && trackingNo.equals(that.trackingNo)
                && originHub.equals(that.originHub);
    }

    @Override
    public int hashCode() {
        return Objects.hash(carrier, trackingNo, originHub, labelVersion);
    }

    @Override
    public String toString() {
        return carrier + ":" + trackingNo + "@" + originHub + " v" + labelVersion;
    }
}
