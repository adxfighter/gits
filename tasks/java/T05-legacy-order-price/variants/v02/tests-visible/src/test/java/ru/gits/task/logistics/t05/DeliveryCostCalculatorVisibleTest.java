package ru.gits.task.logistics.t05;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeliveryCostCalculatorVisibleTest {

    private final DeliveryCostCalculator calculator = new DeliveryCostCalculator();

    @Test
    void lightCityParcel() {
        var shipment = new Shipment(1_000, Shipment.Zone.CITY, false, false, 0);

        assertThat(calculator.calculate(shipment)).isEqualTo(new DeliveryQuote(30_000, 0, 0, 0, 0, 30_000));
    }

    @Test
    void fragileRegionParcelPaysForStartedKilograms() {
        // 7.2 kg -> 8 kg: 450 + 3 x 60 = 630 RUB, fragile 15% = 94.50 RUB
        var shipment = new Shipment(7_200, Shipment.Zone.REGION, true, false, 0);

        assertThat(calculator.calculate(shipment)).isEqualTo(new DeliveryQuote(63_000, 9_450, 0, 0, 0, 72_450));
    }

    @Test
    void intercityParcelPaysForExtraKilograms() {
        var shipment = new Shipment(10_000, Shipment.Zone.INTERCITY, false, false, 0);

        assertThat(calculator.calculate(shipment)).isEqualTo(new DeliveryQuote(115_000, 0, 0, 0, 0, 115_000));
    }

    @Test
    void insuranceHasAMinimum() {
        var shipment = new Shipment(1_000, Shipment.Zone.CITY, false, false, 100_000);

        assertThat(calculator.calculate(shipment)).isEqualTo(new DeliveryQuote(30_000, 0, 0, 5_000, 0, 35_000));
    }
}
