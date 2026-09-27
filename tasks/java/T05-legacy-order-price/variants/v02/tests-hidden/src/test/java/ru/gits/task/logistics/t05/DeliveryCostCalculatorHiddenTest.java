package ru.gits.task.logistics.t05;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DeliveryCostCalculatorHiddenTest {

    private final DeliveryCostCalculator calculator = new DeliveryCostCalculator();

    /** Characterisation of the tariff rules that were already correct. */
    @ParameterizedTest(name = "{0} g, {1}, fragile={2}, express={3}")
    @CsvSource({
            // grams, zone, fragile, express, base, fragile, express, handling, total
            "5000,  CITY,      false, false, 30000,  0,     0,     0,     30000",
            "5001,  CITY,      false, false, 34000,  0,     0,     0,     34000",
            "7500,  CITY,      true,  false, 42000,  6300,  0,     0,     48300",
            "2000,  CITY,      true,  true,  30000,  4500,  17250, 0,     51750",
            "30000, CITY,      false, false, 130000, 0,     0,     0,     130000",
            "31000, CITY,      false, true,  134000, 0,     67000, 50000, 251000",
            "5000,  REGION,    false, true,  45000,  0,     27000, 0,     72000",
            "6500,  REGION,    true,  true,  57000,  8550,  39330, 0,     104880",
            "30001, REGION,    false, false, 201000, 0,     0,     50000, 251000",
            "3000,  INTERCITY, false, false, 70000,  0,     0,     0,     70000",
            "1000,  INTERCITY, false, true,  70000,  0,     56000, 0,     126000",
            "6000,  INTERCITY, true,  false, 79000,  11850, 0,     0,     90850",
            "40000, INTERCITY, true,  false, 385000, 57750, 0,     50000, 492750"
    })
    void existingRulesAreUnchanged(int grams, Shipment.Zone zone, boolean fragile, boolean express,
                                   long base, long fragileFee, long expressFee, long handling, long total) {
        assertThat(calculator.calculate(new Shipment(grams, zone, fragile, express, 0)))
                .isEqualTo(new DeliveryQuote(base, fragileFee, expressFee, 0, handling, total));
    }

    @ParameterizedTest(name = "declared {0} -> insurance {1}")
    @CsvSource({
            "0,        0",
            "400000,   5000",
            "1234549,  12345",
            "1234550,  12346",
            "49999949, 499999",
            "50000000, 500000",
            "60000000, 500000"
    })
    void insuranceIsRoundedAndLimited(long declared, long insurance) {
        assertThat(calculator.calculate(new Shipment(1_000, Shipment.Zone.CITY, false, false, declared)))
                .isEqualTo(new DeliveryQuote(30_000, 0, 0, insurance, 0, 30_000 + insurance));
    }

    @Test
    void fragileExpressIntercityParcelIsChargedOnce() {
        // base 700, fragile 105, express 80% of 805 = 644
        assertThat(calculator.calculate(new Shipment(4_000, Shipment.Zone.INTERCITY, true, true, 0)))
                .isEqualTo(new DeliveryQuote(70_000, 10_500, 64_400, 0, 0, 144_900));
    }

    @Test
    void everythingCombinedForAHeavyIntercityParcel() {
        // 33 kg: base 700 + 28 x 90 = 3220; fragile 483; express 80% of 3703 = 2962.40; handling 500; insurance 500
        assertThat(calculator.calculate(new Shipment(32_500, Shipment.Zone.INTERCITY, true, true, 5_000_000)))
                .isEqualTo(new DeliveryQuote(322_000, 48_300, 296_240, 50_000, 50_000, 766_540));
    }

    @Test
    void handlingAndInsuranceAreNotPartOfPercentageBase() {
        assertThat(calculator.calculate(new Shipment(31_000, Shipment.Zone.CITY, true, true, 0)))
                .isEqualTo(new DeliveryQuote(134_000, 20_100, 77_050, 0, 50_000, 281_150));
        assertThat(calculator.calculate(new Shipment(35_000, Shipment.Zone.REGION, true, true, 10_000_000)))
                .isEqualTo(new DeliveryQuote(225_000, 33_750, 155_250, 100_000, 50_000, 564_000));
    }

    @Test
    void fragileStandardIntercityParcel() {
        assertThat(calculator.calculate(new Shipment(3_000, Shipment.Zone.INTERCITY, true, false, 150_000)))
                .isEqualTo(new DeliveryQuote(70_000, 10_500, 0, 5_000, 0, 85_500));
    }

    @Test
    void totalAlwaysEqualsTheSumOfItsParts() {
        for (Shipment.Zone zone : Shipment.Zone.values()) {
            for (int grams = 500; grams <= 40_000; grams += 1_750) {
                for (int flags = 0; flags < 4; flags++) {
                    DeliveryQuote quote = calculator.calculate(
                            new Shipment(grams, zone, (flags & 1) != 0, (flags & 2) != 0, grams * 37L));
                    assertThat(quote.total()).as("%s %d g flags %d", zone, grams, flags).isEqualTo(
                            quote.base() + quote.fragile() + quote.express() + quote.insurance() + quote.handling());
                }
            }
        }
    }
}
