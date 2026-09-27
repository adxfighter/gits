package ru.gits.task.logistics.t05;

/**
 * Calculates delivery cost: zone tariff, fragile surcharge, express surcharge, insurance, heavy handling.
 */
public class DeliveryCostCalculator {

    private record Tariff(long firstFiveKg, long perExtraKg) {
    }

    private static final Tariff CITY = new Tariff(30_000, 4_000);
    private static final Tariff INTERCITY = new Tariff(70_000, 9_000);
    private static final int INCLUDED_KG = 5;
    private static final int HEAVY_FROM_GRAMS = 30_000;
    private static final long HANDLING_FEE = 50_000;
    private static final long MIN_INSURANCE = 5_000;

    public DeliveryQuote calculate(Shipment shipment) {
        Tariff tariff = shipment.zone() == Shipment.Zone.CITY ? CITY : INTERCITY;
        int kg = (shipment.weightGrams() + 999) / 1000;  // every started kilogram counts

        long base = tariff.firstFiveKg() + Math.max(0, kg - INCLUDED_KG) * tariff.perExtraKg();
        long fragile = shipment.fragile() ? percent(base, 15) : 0;
        long express = shipment.express() ? percent(base + fragile, 50) : 0;
        long insurance = insurance(shipment.declaredValueKopecks());
        long handling = shipment.weightGrams() > HEAVY_FROM_GRAMS ? HANDLING_FEE : 0;

        long total = base + fragile + express + insurance + handling;
        return new DeliveryQuote(base, fragile, express, insurance, handling, total);
    }

    private static long insurance(long declaredValue) {
        if (declaredValue <= 0) {
            return 0;
        }
        return Math.max(MIN_INSURANCE, percent(declaredValue, 1));
    }

    /** Percentage rounded half up to whole kopecks. */
    private static long percent(long amount, int percent) {
        return (amount * percent + 50) / 100;
    }
}
