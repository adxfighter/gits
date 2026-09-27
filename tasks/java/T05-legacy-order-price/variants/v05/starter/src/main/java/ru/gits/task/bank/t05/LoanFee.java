package ru.gits.task.bank.t05;

/**
 * Itemised issuance fee, kopecks.
 *
 * @param baseFee    fee at the rate, before discounts
 * @param commission issuance commission charged by the bank
 * @param insurance  insurance charge
 * @param option     early repayment option charge
 * @param total      amount charged
 */
public record LoanFee(long baseFee, long commission, long insurance, long option, long total) {
}
