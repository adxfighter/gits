package ru.gits.task.energy.t02;

/**
 * A substation of the distribution grid. Locking rule: the load changes only while holding this
 * object's monitor; {@link #addLoad(int)} expects the caller to hold it.
 */
public final class GridNode {

    private final int id;
    private final int capacityMegawatts;
    private int loadMegawatts;

    public GridNode(int id, int capacityMegawatts, int initialLoadMegawatts) {
        if (initialLoadMegawatts < 0 || initialLoadMegawatts > capacityMegawatts) {
            throw new IllegalArgumentException("Initial load must be within capacity");
        }
        this.id = id;
        this.capacityMegawatts = capacityMegawatts;
        this.loadMegawatts = initialLoadMegawatts;
    }

    public int id() {
        return id;
    }

    public int capacity() {
        return capacityMegawatts;
    }

    public synchronized int load() {
        return loadMegawatts;
    }

    /** Whether the load can change by {@code delta}; caller must hold this node's monitor. */
    boolean canChangeBy(int delta) {
        int next = loadMegawatts + delta;
        return next >= 0 && next <= capacityMegawatts;
    }

    /** Caller must hold this node's monitor. */
    void addLoad(int delta) {
        if (!canChangeBy(delta)) {
            throw new IllegalStateException("Node " + id + " cannot change load by " + delta);
        }
        loadMegawatts += delta;
    }
}
