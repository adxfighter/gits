package ru.gits.task.bank.t06;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class MonitoringListVisibleTest {

    private static final String BIK = "044525225";

    @Test
    void rubleAccountsWithTheSameRequisitesAreOneAccount() {
        var list = new MonitoringList();
        list.load(List.of(new Account(BIK, "40817810000000000001")));
        list.load(List.of(new Account(BIK, "40817810000000000001")));

        assertThat(list.size()).isEqualTo(1);
        assertThat(list.isMonitored(new Account(BIK, "40817810000000000001"))).isTrue();
    }

    @Test
    void currencyAccountsWithDifferentCurrencyAreDifferent() {
        var list = new MonitoringList();
        list.load(List.of(
                new CurrencyAccount(BIK, "40817000000000000001", "USD"),
                new CurrencyAccount(BIK, "40817000000000000001", "EUR")));

        assertThat(list.size()).isEqualTo(2);
    }
}
