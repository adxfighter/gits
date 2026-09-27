package ru.gits.task.energy.t05;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Calculates the monthly electricity bill from hourly readings. Legacy code.
 */
public class ElectricityBillCalculator {

    public ElectricityBill calculate(Customer customer, List<HourlyReading> readings, YearMonth month) {
        LocalDateTime from = month.atDay(1).atStartOfDay();
        LocalDateTime to = month.plusMonths(1).atDay(1).atStartOfDay();
        List<HourlyReading> sorted = new ArrayList<>(readings);
        sorted.sort(Comparator.comparing(HourlyReading::start));

        // volumes by zone
        long singleKwh = 0;
        long twoNightKwh = 0;
        long twoDayKwh = 0;
        long threeNightKwh = 0;
        long threePeakKwh = 0;
        long threeHalfKwh = 0;
        for (HourlyReading r : sorted) {
            if (!inBillingPeriod(r.end(), from, to)) {
                continue;
            }
            int h = r.start().getHour();
            if (customer.plan() == TariffPlan.SINGLE) {
                singleKwh = singleKwh + r.kilowattHours();
            } else if (customer.plan() == TariffPlan.TWO_ZONE) {
                if (h >= 23 || h < 7) {
                    twoNightKwh = twoNightKwh + r.kilowattHours();
                } else {
                    twoDayKwh = twoDayKwh + r.kilowattHours();
                }
            } else {
                if (h >= 23 || h < 7) {
                    threeNightKwh = threeNightKwh + r.kilowattHours();
                } else if (h >= 7 && h < 10 || h >= 17 && h < 21) {
                    threePeakKwh = threePeakKwh + r.kilowattHours();
                } else {
                    threeHalfKwh = threeHalfKwh + r.kilowattHours();
                }
            }
        }
        long nightKwh;
        long peakKwh;
        long dayKwh;
        if (customer.plan() == TariffPlan.SINGLE) {
            nightKwh = 0;
            peakKwh = 0;
            dayKwh = singleKwh;
        } else if (customer.plan() == TariffPlan.TWO_ZONE) {
            nightKwh = twoNightKwh;
            peakKwh = 0;
            dayKwh = twoDayKwh;
        } else {
            nightKwh = threeNightKwh;
            peakKwh = threePeakKwh;
            dayKwh = threeHalfKwh;
        }

        long norm = 150;
        if (customer.electricStove()) {
            norm = 250;
        }
        long consumed = 0;
        long singleCost = 0;
        long nightCost = 0;
        long peakCost = 0;
        long dayCost = 0;

        if (customer.plan() == TariffPlan.SINGLE) {
            for (HourlyReading r : sorted) {
                if (!inBillingPeriod(r.end(), from, to)) {
                    continue;
                }
                long kwh = r.kilowattHours();
                long inNorm = 0;
                long inBase = 0;
                long inExcess = 0;
                if (consumed + kwh <= norm) {
                    inNorm = kwh;
                } else if (consumed >= 600) {
                    inExcess = kwh;
                } else {
                    if (consumed < norm) {
                        inNorm = norm - consumed;
                    }
                    if (consumed + kwh > 600) {
                        inExcess = consumed + kwh - 600;
                    }
                    inBase = kwh - inNorm - inExcess;
                }
                consumed = consumed + kwh;
                singleCost = singleCost + inNorm * 560 * 80 / 100 + inBase * 560 + inExcess * 560 * 130 / 100;
            }
        } else {
            for (HourlyReading r : sorted) {
                if (!inBillingPeriod(r.start(), from, to)) {
                    continue;
                }
                int h = r.start().getHour();
                long kwh = r.kilowattHours();
                if (h >= 23 || h < 7) {
                    long normLeft = Math.max(0, norm - consumed);
                    long inNorm = Math.min(kwh, normLeft);
                    long baseLeft = Math.max(0, 600 - consumed - inNorm);
                    long inBase = Math.min(kwh - inNorm, baseLeft);
                    long inExcess = kwh - inNorm - inBase;
                    nightCost = nightCost + inNorm * 256;
                    nightCost = nightCost + inBase * 320;
                    nightCost = nightCost + inExcess * 416;
                    consumed = consumed + kwh;
                } else if (customer.plan() == TariffPlan.TWO_ZONE) {
                    long normLeft = Math.max(0, norm - consumed);
                    long inNorm = Math.min(kwh, normLeft);
                    long baseLeft = Math.max(0, 600 - consumed - inNorm);
                    long inBase = Math.min(kwh - inNorm, baseLeft);
                    long inExcess = kwh - inNorm - inBase;
                    dayCost = dayCost + inNorm * 640 * 80 / 100;
                    dayCost = dayCost + inBase * 640;
                    dayCost = dayCost + inExcess * 640 * 130 / 100;
                    consumed = consumed + kwh;
                } else if (h >= 7 && h < 10 || h >= 17 && h < 21) {
                    long normLeft = Math.max(0, norm - consumed);
                    long inNorm = Math.min(kwh, normLeft);
                    long baseLeft = Math.max(0, 600 - consumed - inNorm);
                    long inBase = Math.min(kwh - inNorm, baseLeft);
                    long inExcess = kwh - inNorm - inBase;
                    peakCost = peakCost + inNorm * 624;
                    peakCost = peakCost + inBase * 780;
                    peakCost = peakCost + inExcess * 1014;
                    consumed = consumed + kwh;
                } else {
                    long normLeft = Math.max(0, norm - consumed);
                    long inNorm = Math.min(kwh, normLeft);
                    long baseLeft = Math.max(0, 600 - consumed - inNorm);
                    long inBase = Math.min(kwh - inNorm, baseLeft);
                    long inExcess = kwh - inNorm - inBase;
                    dayCost = dayCost + inNorm * 560 * 80 / 100;
                    dayCost = dayCost + inBase * 560;
                    dayCost = dayCost + inExcess * 560 * 130 / 100;
                    consumed = consumed + kwh;
                }
            }
        }
        long energyCost = singleCost + nightCost + peakCost + dayCost;

        long serviceFee = 15000;
        if (customer.plan() == TariffPlan.SINGLE) {
            serviceFee = 12000;
        }
        long ruralDiscount = 0;
        if (customer.rural()) {
            ruralDiscount = energyCost * 30 / 100;
            if (energyCost * 30 % 100 >= 50) {
                ruralDiscount = ruralDiscount + 1;
            }
        }
        long total = energyCost - ruralDiscount + serviceFee;
        return new ElectricityBill(nightKwh, peakKwh, dayKwh, energyCost, ruralDiscount, serviceFee, total);
    }

    private static boolean inBillingPeriod(LocalDateTime moment, LocalDateTime from, LocalDateTime to) {
        return moment.isAfter(from) && !moment.isAfter(to);
    }
}
