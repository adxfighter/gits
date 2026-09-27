package ru.gits.task.telecom.t02;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SettlementServiceVisibleTest {

    @Test
    void settlementKeepsTheClearingFee() {
        var clearing = new OperatorAccount(999, "Клиринг", 0);
        var service = new SettlementService(clearing);
        var alpha = new OperatorAccount(1, "Альфа", 100_000);
        var beta = new OperatorAccount(2, "Бета", 0);

        service.settle(alpha, beta, 10_000);

        assertThat(alpha.balance()).isEqualTo(90_000);
        assertThat(beta.balance()).isEqualTo(9_850);
        assertThat(clearing.balance()).isEqualTo(150);
    }

    @Test
    void feeIsRoundedDown() {
        assertThat(SettlementService.feeOf(99)).isEqualTo(1);
        assertThat(SettlementService.feeOf(66)).isZero();
    }
}
