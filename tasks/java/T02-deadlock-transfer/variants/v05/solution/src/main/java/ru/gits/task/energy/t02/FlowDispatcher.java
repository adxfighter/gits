package ru.gits.task.energy.t02;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.IntSupplier;

/**
 * Applies load shifts to the grid. Operators call {@link #shift}, the planner calls {@link #applyBatch};
 * both run concurrently. Lock protocol: all involved nodes are locked once each, in ascending node id.
 */
public final class FlowDispatcher {

    /**
     * Shifts load between two nodes atomically.
     *
     * @throws IllegalStateException when a node limit would be violated (nothing changes)
     */
    public void shift(GridNode from, GridNode to, int megawatts) {
        List<Flow> flows = List.of(new Flow(from, to, megawatts));
        withNodesLocked(flows, () -> apply(flows));
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
        List<Flow> batch = List.copyOf(flows);
        return withNodesLocked(batch, () -> apply(batch));
    }

    private static int withNodesLocked(List<Flow> flows, IntSupplier action) {
        TreeSet<GridNode> nodes = new TreeSet<>(Comparator.comparingInt(GridNode::id));
        for (Flow flow : flows) {
            nodes.add(flow.from());
            nodes.add(flow.to());
        }
        return lockInOrder(List.copyOf(nodes), 0, action);
    }

    private static int lockInOrder(List<GridNode> ordered, int index, IntSupplier action) {
        if (index == ordered.size()) {
            return action.getAsInt();
        }
        synchronized (ordered.get(index)) {
            return lockInOrder(ordered, index + 1, action);
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
