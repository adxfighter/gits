package ru.gits.task.bank.t05;

/**
 * A consumer loan application.
 *
 * @param amountKopecks        requested amount
 * @param salaryClient         the client receives salary to this bank
 * @param online               the application was submitted online
 * @param insurance            the client opted in to loan insurance
 * @param earlyRepaymentOption the client bought the free early repayment option
 */
public record LoanApplication(long amountKopecks, boolean salaryClient, boolean online, boolean insurance,
                              boolean earlyRepaymentOption) {

    public LoanApplication {
        if (amountKopecks <= 0) {
            throw new IllegalArgumentException("Loan amount must be positive");
        }
    }
}
