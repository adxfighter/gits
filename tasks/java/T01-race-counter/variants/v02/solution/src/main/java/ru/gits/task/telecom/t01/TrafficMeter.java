package ru.gits.task.telecom.t01;

/**
 * Accumulates the traffic of one subscriber. Network gateways call {@link #addPacket(int)}
 * concurrently; billing reads {@link #snapshot()} from its own thread.
 */
public final class TrafficMeter {

    /** Largest packet the network can carry (jumbo frame). */
    public static final int MAX_PACKET_BYTES = 9_000;

    private final String subscriberId;
    private long packets;
    private long totalBytes;

    public TrafficMeter(String subscriberId) {
        this.subscriberId = subscriberId;
    }

    /**
     * Accounts one packet.
     *
     * @param bytes packet size, 1..{@link #MAX_PACKET_BYTES}
     * @throws IllegalArgumentException for an impossible packet size
     */
    public void addPacket(int bytes) {
        if (bytes <= 0 || bytes > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Impossible packet size " + bytes + " for " + subscriberId);
        }
        synchronized (this) {
            packets++;
            totalBytes += bytes;
        }
    }

    /** Packets and bytes accounted so far, taken from the same moment. */
    public synchronized TrafficSnapshot snapshot() {
        return new TrafficSnapshot(packets, totalBytes);
    }

    public String subscriberId() {
        return subscriberId;
    }
}
