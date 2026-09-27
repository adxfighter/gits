package ru.gits.task.telecom.t02;

import java.util.Objects;

/**
 * Settles mutual obligations between operators. The clearing house keeps a fee of every settlement.
 * Settlements between different operator pairs run concurrently.
 */
public final class SettlementService {

    /** Clearing fee: 1.5% of the amount, rounded down to whole kopecks. */
    static final long FEE_PER_MILLE = 15;

    private final OperatorAccount clearingHouse;

    public SettlementService(OperatorAccount clearingHouse) {
        this.clearingHouse = Objects.requireNonNull(clearingHouse, "clearingHouse");
    }

    /**
     * Pays {@code amount} from payer to payee; the clearing fee is kept from the payee's part.
     *
     * @throws IllegalArgumentException for a non-positive amount or a payment to oneself
     * @throws IllegalStateException    when the payer cannot pay (nothing changes)
     */
    public void settle(OperatorAccount payer, OperatorAccount payee, long amountKopecks) {
        Objects.requireNonNull(payer, "payer");
        Objects.requireNonNull(payee, "payee");
        if (amountKopecks <= 0) {
            throw new IllegalArgumentException("Amount must be positive: " + amountKopecks);
        }
        if (payer.operatorCode() == payee.operatorCode()) {
            throw new IllegalArgumentException("Operator cannot settle with itself: " + payer.name());
        }
        // Lock order: operators by code, the clearing house always last
        OperatorAccount first = payer.operatorCode() < payee.operatorCode() ? payer : payee;
        OperatorAccount second = first == payer ? payee : payer;
        synchronized (first) {
            synchronized (second) {
                payer.withdraw(amountKopecks);
                creditWithFee(payee, amountKopecks);
            }
        }
    }

    /** Fee of a settlement amount. */
    public static long feeOf(long amountKopecks) {
        return amountKopecks * FEE_PER_MILLE / 1000;
    }

    /** Caller must hold the payee's monitor. */
    private void creditWithFee(OperatorAccount payee, long amountKopecks) {
        long fee = feeOf(amountKopecks);
        payee.deposit(amountKopecks - fee);
        synchronized (clearingHouse) {
            clearingHouse.deposit(fee);
        }
    }
}
