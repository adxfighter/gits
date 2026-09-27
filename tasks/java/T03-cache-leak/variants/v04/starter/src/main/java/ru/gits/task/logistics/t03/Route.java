package ru.gits.task.logistics.t03;

import java.util.List;
import java.util.Objects;

/**
 * A delivery route between two cities.
 *
 * @param from       departure city
 * @param to         destination city
 * @param waypoints  intermediate points in driving order
 * @param kilometres route length
 */
public record Route(String from, String to, List<String> waypoints, int kilometres) {

    public Route {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        waypoints = List.copyOf(waypoints);
        if (kilometres <= 0) {
            throw new IllegalArgumentException("Route length must be positive");
        }
    }
}
