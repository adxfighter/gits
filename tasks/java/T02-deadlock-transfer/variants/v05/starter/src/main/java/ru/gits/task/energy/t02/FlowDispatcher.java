package ru.gits.task.energy.t02;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Applies load shifts to the grid. Operators call {@link #shift}, the planner calls {@link #applyBatch};
 * both run concurrently.
 */
public final class FlowDispatcher {

    /**
     * Shifts load between two nodes atomically.
     *
     * @throws IllegalStateException when a node limit would be violated (nothing changes)
     */
    public void shift(GridNode from, GridNode to, int megawatts) {
        Flow flow = new Flow(from, to, megawatts);
        synchronized (flow.from()) {
            synchronized (flow.to()) {
                apply(List.of(flow));
            }
        }
    }

    /**
     * Applies all flows or none of them.
     *
     * @return total shifted load
     * @throws IllegalStateException when any flow would violate a node limit (nothing changes)
     */
    public int applyBatch(List<Flow> flows) {
        Objects.requireNonNull(flows, "flows");
        if (flows.isEmpty()) {
            return 0;
        }
        return lockFlows(flows, 0);
    }

    /** Locks the nodes of each flow in the order they appear in the batch, then applies the batch. */
    private int lockFlows(List<Flow> flows, int index) {
        if (index == flows.size()) {
            return apply(flows);
        }
        Flow flow = flows.get(index);
        synchronized (flow.from()) {
            synchronized (flow.to()) {
                return lockFlows(flows, index + 1);
            }
        }
    }

    /** Checks the net change of every node first, then applies; callers hold all involved monitors. */
    private static int apply(List<Flow> flows) {
        Map<GridNode, Integer> netChange = new HashMap<>();
        int shifted = 0;
        for (Flow flow : flows) {
            netChange.merge(flow.from(), -flow.megawatts(), Integer::sum);
            netChange.merge(flow.to(), flow.megawatts(), Integer::sum);
            shifted += flow.megawatts();
        }
        netChange.forEach((node, delta) -> {
            if (!node.canChangeBy(delta)) {
                throw new IllegalStateException("Batch violates the limits of node " + node.id());
            }
        });
        netChange.forEach(GridNode::addLoad);
        return shifted;
    }
}
