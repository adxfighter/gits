package ru.gits.task.bank.t06;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class AccountEqualityHiddenTest {

    private static final String BIK = "044525225";

    private static List<Account> rubleBatch() {
        List<Account> batch = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            batch.add(new Account(BIK, "MIGR-" + i));
        }
        return batch;
    }

    private static List<CurrencyAccount> currencyBatch() {
        List<CurrencyAccount> batch = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            batch.add(new CurrencyAccount(BIK, "MIGR-" + i, "USD"));
        }
        return batch;
    }

    @Test
    void loadOrderDoesNotMatter() {
        var rublesFirst = new MonitoringList();
        rublesFirst.load(rubleBatch());
        rublesFirst.load(currencyBatch());

        var currencyFirst = new MonitoringList();
        currencyFirst.load(currencyBatch());
        currencyFirst.load(rubleBatch());

        assertThat(rublesFirst.size()).isEqualTo(100);
        assertThat(currencyFirst.size()).isEqualTo(100);
    }

    @Test
    void equalsIsSymmetricAcrossTheHierarchy() {
        var ruble = new Account(BIK, "MIGR-1");
        var usd = new CurrencyAccount(BIK, "MIGR-1", "USD");

        assertThat(ruble.equals(usd)).isFalse();
        assertThat(usd.equals(ruble)).isFalse();
    }

    @Test
    void equalsIsTransitiveAndConsistentWithHashCode() {
        var a = new CurrencyAccount(BIK, "MIGR-2", "EUR");
        var b = new CurrencyAccount(BIK, "MIGR-2", "EUR");
        var c = new CurrencyAccount(new String(BIK), new String("MIGR-2"), new String("EUR"));

        assertThat(a).isEqualTo(b);
        assertThat(b).isEqualTo(c);
        assertThat(a).isEqualTo(c);
        assertThat(a.hashCode()).isEqualTo(b.hashCode()).isEqualTo(c.hashCode());
        assertThat(a).isNotEqualTo(new CurrencyAccount(BIK, "MIGR-2", "USD")).isNotEqualTo(null);
    }

    @Test
    void currencyAccountDoesNotReleaseTheRubleAccount() {
        var list = new MonitoringList();
        list.load(List.of(new Account(BIK, "MIGR-3")));

        assertThat(list.isMonitored(new CurrencyAccount(BIK, "MIGR-3", "USD"))).isFalse();
        assertThat(list.release(new CurrencyAccount(BIK, "MIGR-3", "USD"))).isFalse();
        assertThat(list.isMonitored(new Account(BIK, "MIGR-3"))).isTrue();
    }

    @Test
    void rubleAccountIsNotFoundByACurrencyAccountAndViceVersa() {
        var list = new MonitoringList();
        list.load(List.of(new CurrencyAccount(BIK, "MIGR-4", "USD")));

        assertThat(list.isMonitored(new Account(BIK, "MIGR-4"))).isFalse();
        assertThat(list.isMonitored(new CurrencyAccount(BIK, "MIGR-4", "USD"))).isTrue();
        assertThat(list.isMonitored(new CurrencyAccount(BIK, "MIGR-4", "EUR"))).isFalse();
    }
}
