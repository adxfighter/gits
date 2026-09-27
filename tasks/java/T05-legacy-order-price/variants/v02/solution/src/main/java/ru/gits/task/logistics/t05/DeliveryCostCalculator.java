package ru.gits.task.logistics.t05;

/**
 * Calculates the delivery cost of a parcel according to the courier tariff sheet.
 */
public class DeliveryCostCalculator {

    public DeliveryQuote calculate(Shipment shipment) {
        long base = 0;
        long fragile = 0;
        long express = 0;
        long insurance = 0;
        long handling = 0;
        long total = 0;

        int grams = shipment.weightGrams();
        // every started kilogram counts
        int kg = (grams + 999) / 1000;

        if (shipment.zone() == Shipment.Zone.CITY) {
            if (!shipment.express()) {
                // ---- city, standard ----
                base = 30000;
                if (kg > 5) {
                    int extraKg = kg - 5;
                    base = base + extraKg * 4000;
                }
                total = base;

                if (shipment.fragile()) {
                    // 15% careful handling
                    fragile = (base * 15 + 50) / 100;
                    total = total + fragile;
                }

                if (grams > 30000) {
                    // heavy parcel, loader required
                    handling = 50000;
                    total = total + handling;
                }
            } else {
                // ---- city, express ----
                base = 30000;
                if (kg > 5) {
                    int extraKg = kg - 5;
                    base = base + extraKg * 4000;
                }
                total = base;

                long expressBase = base;
                if (shipment.fragile()) {
                    // 15% careful handling
                    fragile = (base * 15 + 50) / 100;
                    total = total + fragile;
                    expressBase = expressBase + fragile;
                }

                // next day: +50%
                express = (expressBase * 50 + 50) / 100;
                total = total + express;

                if (grams > 30000) {
                    // heavy parcel, loader required
                    handling = 50000;
                    total = total + handling;
                }
            }
        } else if (shipment.zone() == Shipment.Zone.REGION) {
            // region tariff (added in 2021)
            long tariff;
            if (kg <= 5) {
                tariff = 45000;
            } else {
                long extraKg = kg - 5;
                long extra = extraKg * 6000;
                tariff = 45000 + extra;
            }
            base = tariff;

            if (shipment.express()) {
                // ---- region, express ----
                long surcharge = 0;
                if (shipment.fragile()) {
                    fragile = Math.round(base * 0.15);
                    surcharge = surcharge + fragile;
                }

                // next day: +60%
                long expressAmount = base + surcharge;
                express = (expressAmount * 60 + 50) / 100;
                surcharge = surcharge + express;

                if (kg > 30) {
                    handling = 50000;
                }

                total = base + surcharge + handling;
            } else {
                // ---- region, standard ----
                if (shipment.fragile()) {
                    fragile = Math.round(base * 0.15);
                }

                if (kg > 30) {
                    handling = 50000;
                }

                total = base + fragile + handling;
            }
        } else {
            if (shipment.express()) {
                // ---- intercity, express ----
                long tariff = 70000;
                if (kg > 5) {
                    tariff = tariff + (kg - 5) * 9000L;
                }
                base = tariff;

                long chargeable = base;
                if (shipment.fragile()) {
                    // 15% careful handling
                    fragile = (base * 15 + 50) / 100;
                    chargeable = chargeable + fragile;
                }

                // next day by air: +80%
                express = (chargeable * 80 + 50) / 100;

                if (grams > 30000) {
                    // heavy parcel, loader required
                    handling = 50000;
                }

                total = chargeable + express + handling;
            } else {
                // ---- intercity, standard ----
                long tariff = 70000;
                if (kg > 5) {
                    tariff = tariff + (kg - 5) * 9000L;
                }
                base = tariff;

                if (shipment.fragile()) {
                    // 15% careful handling
                    fragile = (base * 15 + 50) / 100;
                }

                if (grams > 30000) {
                    // heavy parcel, loader required
                    handling = 50000;
                }

                total = base + fragile + handling;
            }
        }

        // insurance: 1% of declared value, 50 RUB .. 5000 RUB
        long declared = shipment.declaredValueKopecks();
        if (declared > 0) {
            insurance = (declared + 50) / 100;
            if (insurance < 5000) {
                insurance = 5000;
            }
            if (insurance > 500000) {
                insurance = 500000;
            }
            total = total + insurance;
        }

        return new DeliveryQuote(base, fragile, express, insurance, handling, total);
    }
}
