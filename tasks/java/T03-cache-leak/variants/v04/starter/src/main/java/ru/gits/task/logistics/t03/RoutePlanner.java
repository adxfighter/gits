package ru.gits.task.logistics.t03;

/**
 * Builds routes taking the current road situation into account. Expensive: seconds per call.
 */
@FunctionalInterface
public interface RoutePlanner {

    Route plan(String from, String to);
}
