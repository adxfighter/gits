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
