package ru.gits.task.energy.t02;

import java.util.Objects;

/**
 * A planned load shift between two grid nodes.
 *
 * @param from      node the load leaves
 * @param to        node the load moves to
 * @param megawatts shifted load, positive
 */
public record Flow(GridNode from, GridNode to, int megawatts) {

    public Flow {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (megawatts <= 0) {
            throw new IllegalArgumentException("A flow must carry positive load: " + megawatts);
        }
        if (from.id() == to.id()) {
            throw new IllegalArgumentException("A flow cannot start and end at node " + from.id());
        }
    }
}
