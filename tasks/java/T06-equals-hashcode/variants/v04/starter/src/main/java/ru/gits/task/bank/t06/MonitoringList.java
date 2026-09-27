package ru.gits.task.bank.t06;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Accounts under financial monitoring.
 */
public final class MonitoringList {

    private final Set<Account> accounts = new HashSet<>();

    public void load(Collection<? extends Account> batch) {
        accounts.addAll(batch);
    }

    public boolean isMonitored(Account account) {
        return accounts.contains(account);
    }

    public boolean release(Account account) {
        return accounts.remove(account);
    }

    public int size() {
        return accounts.size();
    }
}
