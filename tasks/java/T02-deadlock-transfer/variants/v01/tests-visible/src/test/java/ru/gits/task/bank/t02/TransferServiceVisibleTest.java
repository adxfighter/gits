package ru.gits.task.bank.t02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TransferServiceVisibleTest {

    private final TransferService service = new TransferService();

    @Test
    void movesMoneyBetweenAccounts() {
        var alice = new Account(1, 10_000);
        var bob = new Account(2, 500);

        service.transfer(alice, bob, 2_500);

        assertThat(alice.balance()).isEqualTo(7_500);
        assertThat(bob.balance()).isEqualTo(3_000);
    }

    @Test
    void rejectsTransferWithoutEnoughMoney() {
        var alice = new Account(1, 100);
        var bob = new Account(2, 0);

        assertThatThrownBy(() -> service.transfer(alice, bob, 101)).isInstanceOf(IllegalStateException.class);
        assertThat(alice.balance()).isEqualTo(100);
        assertThat(bob.balance()).isZero();
    }
}
