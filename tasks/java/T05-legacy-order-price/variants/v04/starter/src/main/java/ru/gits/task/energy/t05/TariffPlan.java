package ru.gits.task.energy.t05;

/**
 * Tariff plan of a customer.
 */
public enum TariffPlan {
    /** One price for every hour. */
    SINGLE,
    /** Night and day zones. */
    TWO_ZONE,
    /** Night, peak and half-peak zones. */
    THREE_ZONE
}
