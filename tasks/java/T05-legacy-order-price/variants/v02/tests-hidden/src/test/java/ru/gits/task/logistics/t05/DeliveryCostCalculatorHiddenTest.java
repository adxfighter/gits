package ru.gits.task.logistics.t05;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DeliveryCostCalculatorHiddenTest {

    private final DeliveryCostCalculator calculator = new DeliveryCostCalculator();

    /** Characterisation of the tariff rules that were already correct. */
    @ParameterizedTest(name = "{0} g, {1}, fragile={2}, express={3}, declared={4}")
    @CsvSource({
            // grams, zone, fragile, express, declared, base, fragile, express, insurance, handling, total
            "5000,  CITY,      false, false, 0,       30000,  0,    0,     0,     0,     30000",
            "5001,  CITY,      false, false, 0,       34000,  0,    0,     0,     0,     34000",
            "7500,  CITY,      true,  false, 0,       42000,  6300, 0,     0,     0,     48300",
            "2000,  CITY,      true,  true,  0,       30000,  4500, 17250, 0,     0,     51750",
            "1000,  CITY,      false, false, 1234567, 30000,  0,    0,     12346, 0,     42346",
            "31000, CITY,      false, false, 0,       134000, 0,    0,     0,     50000, 184000",
            "30000, CITY,      false, false, 0,       130000, 0,    0,     0,     0,     130000",
            "3000,  INTERCITY, false, false, 0,       70000,  0,    0,     0,     0,     70000",
            "1000,  INTERCITY, false, true,  0,       70000,  0,    35000, 0,     0,     105000",
            "6000,  INTERCITY, false, false, 600000,  79000,  0,    0,     6000,  0,     85000"
    })
    void existingRulesAreUnchanged(int grams, Shipment.Zone zone, boolean fragile, boolean express, long declared,
                                   long base, long fragileFee, long expressFee, long insurance, long handling,
                                   long total) {
        assertThat(calculator.calculate(new Shipment(grams, zone, fragile, express, declared)))
                .isEqualTo(new DeliveryQuote(base, fragileFee, expressFee, insurance, handling, total));
    }

    @Test
    void fragileIntercityParcelIsChargedOnce() {
        assertThat(calculator.calculate(new Shipment(3_000, Shipment.Zone.INTERCITY, true, false, 0)))
                .isEqualTo(new DeliveryQuote(70_000, 10_500, 0, 0, 0, 80_500));
    }

    @Test
    void fragileExpressIntercityParcel() {
        // base 70000, fragile 10500, express 50% of 80500 = 40250
        assertThat(calculator.calculate(new Shipment(4_000, Shipment.Zone.INTERCITY, true, true, 0)))
                .isEqualTo(new DeliveryQuote(70_000, 10_500, 40_250, 0, 0, 120_750));
    }

    @Test
    void everythingCombinedForAHeavyIntercityParcel() {
        // 33 kg: base 70000 + 28 x 9000 = 322000; fragile 48300; express 50% of 370300 = 185150
        assertThat(calculator.calculate(new Shipment(32_500, Shipment.Zone.INTERCITY, true, true, 5_000_000)))
                .isEqualTo(new DeliveryQuote(322_000, 48_300, 185_150, 50_000, 50_000, 655_450));
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
