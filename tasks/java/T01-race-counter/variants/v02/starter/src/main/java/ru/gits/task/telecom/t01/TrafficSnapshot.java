package ru.gits.task.telecom.t01;

/**
 * Point-in-time view of a subscriber's traffic.
 *
 * @param packets    number of packets
 * @param totalBytes total size of those packets in bytes
 */
public record TrafficSnapshot(long packets, long totalBytes) {

    public static final TrafficSnapshot EMPTY = new TrafficSnapshot(0, 0);

    /** Average packet size in bytes, 0 when there are no packets. */
    public double averagePacketBytes() {
        return packets == 0 ? 0 : (double) totalBytes / packets;
    }

    public TrafficSnapshot plus(long packetBytes) {
        return new TrafficSnapshot(packets + 1, totalBytes + packetBytes);
    }
}
