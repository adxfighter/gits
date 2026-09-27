package ru.gits.task.telecom.t04;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class CallChargerVisibleTest {

    private static final CallRecord CALL = new CallRecord("c-1", "acc-1", "79005550000", 120);

    @Test
    void chargesByTheRatingRules() {
        var charger = new CallCharger(new TariffCatalog());

        long charge = charger.charge(CALL, call -> call.seconds() * 2L);

        assertThat(charge).isEqualTo(240);
    }

    @Test
    void callIsRatedAgainWhenTheTariffChangesDuringRating() {
        var tariffs = new TariffCatalog();
        var charger = new CallCharger(tariffs);
        var ratings = new AtomicInteger();

        long charge = charger.charge(CALL, call -> {
            if (ratings.incrementAndGet() == 1) {
                tariffs.tariffChanged("SMART-5");
                return 100;
            }
            return 150;
        });

        assertThat(charge).isEqualTo(150);
        assertThat(ratings.get()).isEqualTo(2);
    }
}
