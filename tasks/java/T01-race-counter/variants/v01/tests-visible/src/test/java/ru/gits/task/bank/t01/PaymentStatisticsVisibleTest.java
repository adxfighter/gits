package ru.gits.task.bank.t01;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PaymentStatisticsVisibleTest {

    @Test
    void countsPaymentsProcessedByOneThread() {
        var statistics = new PaymentStatistics();

        statistics.record(new Payment("p-1", 10_000, "40817810000000000001"));
        statistics.record(new Payment("p-2", 250, "40817810000000000002"));

        assertThat(statistics.processedCount()).isEqualTo(2);
    }

    @Test
    void rejectsPaymentWithoutAmount() {
        var statistics = new PaymentStatistics();

        assertThatThrownBy(() -> statistics.record(new Payment("p-0", 0, "40817810000000000001")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(statistics.processedCount()).isZero();
    }
}
