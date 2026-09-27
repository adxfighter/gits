package ru.gits.task.logistics.t05;

/**
 * A parcel to deliver.
 *
 * @param weightGrams          parcel weight, positive
 * @param zone                 delivery zone
 * @param fragile              needs careful handling
 * @param express              next-day delivery
 * @param declaredValueKopecks declared value for insurance, 0 when not declared
 */
public record Shipment(int weightGrams, Zone zone, boolean fragile, boolean express, long declaredValueKopecks) {

    public enum Zone {
        CITY,
        INTERCITY
    }

    public Shipment {
        if (weightGrams <= 0 || declaredValueKopecks < 0 || zone == null) {
            throw new IllegalArgumentException("Invalid shipment");
        }
    }
}
