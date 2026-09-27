package ru.gits.task.logistics.t05;

/**
 * Calculates delivery cost. Legacy code: the intercity branch was copied from the city branch.
 */
public class DeliveryCostCalculator {

    public DeliveryQuote calculate(Shipment shipment) {
        long base;
        long fragile = 0;
        long express = 0;
        long insurance = 0;
        long handling = 0;
        long total;

        int kg = shipment.weightGrams() / 1000;
        if (shipment.weightGrams() % 1000 != 0) {
            kg = kg + 1;
        }

        if (shipment.zone() == Shipment.Zone.CITY) {
            // city tariff
            if (kg <= 5) {
                base = 30000;
            } else {
                base = 30000 + (kg - 5) * 4000;
            }
            total = base;
            if (shipment.fragile()) {
                fragile = (base * 15 + 50) / 100;
                total = total + fragile;
            }
            if (shipment.express()) {
                long expressBase = base;
                if (shipment.fragile()) {
                    expressBase = base + fragile;
                }
                express = (expressBase * 50 + 50) / 100;
                total = total + express;
            }
            if (shipment.declaredValueKopecks() > 0) {
                insurance = (shipment.declaredValueKopecks() + 50) / 100;
                if (insurance < 5000) {
                    insurance = 5000;
                }
                total = total + insurance;
            }
            if (shipment.weightGrams() > 30000) {
                handling = 50000;
                total = total + handling;
            }
        } else {
            // intercity tariff
            if (kg <= 5) {
                base = 70000;
            } else {
                base = 70000 + (kg - 5) * 9000;
            }
            total = base;
            if (shipment.fragile()) {
                fragile = (base * 15 + 50) / 100;
                total = total + fragile;
            }
            if (shipment.express()) {
                long expressBase = base;
                if (shipment.fragile()) {
                    expressBase = base + fragile;
                }
                express = (expressBase * 50 + 50) / 100;
                total = total + express;
            }
            if (shipment.fragile()) {
                // careful handling for long distance
                total = total + fragile;
            }
            if (shipment.declaredValueKopecks() > 0) {
                insurance = (shipment.declaredValueKopecks() + 50) / 100;
                if (insurance < 5000) {
                    insurance = 5000;
                }
                total = total + insurance;
            }
            if (shipment.weightGrams() > 30000) {
                handling = 50000;
                total = total + handling;
            }
        }

        return new DeliveryQuote(base, fragile, express, insurance, handling, total);
    }
}
