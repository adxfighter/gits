package ru.gits.task.energy.t05;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

/**
 * Calculates the monthly electricity bill from hourly readings: zone price per kWh, social norm and excess
 * coefficients in chronological order, rural discount, service fee.
 */
public class ElectricityBillCalculator {

    private static final long NIGHT_PRICE = 320;
    private static final long DAY_PRICE = 640;
    private static final long SOCIAL_NORM_KWH = 150;
    private static final long EXCESS_FROM_KWH = 600;
    private static final int SOCIAL_NORM_PERCENT = 80;
    private static final int EXCESS_PERCENT = 130;
    private static final int RURAL_DISCOUNT_PERCENT = 30;
    private static final long SERVICE_FEE = 12_000;

    public ElectricityBill calculate(Customer customer, List<HourlyReading> readings, YearMonth month) {
        List<HourlyReading> hours = readings.stream()
                .filter(reading -> YearMonth.from(reading.start()).equals(month))  // hours that start in the month
                .sorted(Comparator.comparing(HourlyReading::start))
                .toList();

        long dayKwh = 0;
        long nightKwh = 0;
        long energyCost = 0;
        long consumed = 0;
        for (HourlyReading hour : hours) {
            boolean night = isNight(hour);
            long price = night ? NIGHT_PRICE : DAY_PRICE;
            for (int kwh = 0; kwh < hour.kilowattHours(); kwh++) {
                consumed++;
                energyCost += priceWithCoefficient(price, consumed);
            }
            if (night) {
                nightKwh += hour.kilowattHours();
            } else {
                dayKwh += hour.kilowattHours();
            }
        }

        long ruralDiscount = customer.rural() ? percent(energyCost, RURAL_DISCOUNT_PERCENT) : 0;
        long total = energyCost - ruralDiscount + SERVICE_FEE;
        return new ElectricityBill(dayKwh, nightKwh, energyCost, ruralDiscount, SERVICE_FEE, total);
    }

    /** Night zone: hours starting from 23:00 to 06:59. */
    private static boolean isNight(HourlyReading reading) {
        int hour = reading.start().getHour();
        return hour >= 23 || hour < 7;
    }

    /** Price of the n-th kWh of the month (1-based), with the social norm or excess coefficient. */
    private static long priceWithCoefficient(long price, long nthKwh) {
        if (nthKwh <= SOCIAL_NORM_KWH) {
            return price * SOCIAL_NORM_PERCENT / 100;
        }
        if (nthKwh > EXCESS_FROM_KWH) {
            return price * EXCESS_PERCENT / 100;
        }
        return price;
    }

    private static long percent(long amount, int percent) {
        return (amount * percent + 50) / 100;
    }
}
