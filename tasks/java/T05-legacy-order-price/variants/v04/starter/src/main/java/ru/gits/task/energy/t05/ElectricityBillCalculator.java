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
        List<HourlyReading> sorted = new ArrayList<>(readings);
        sorted.sort(Comparator.comparing(HourlyReading::start));

        long dayKwh = 0;
        long nightKwh = 0;
        long energyCost = 0;
        long consumed = 0;

        for (HourlyReading reading : sorted) {
            // the hour is billed in the month in which it is completed
            LocalDateTime end = reading.end();
            if (end.getYear() != month.getYear() || end.getMonthValue() != month.getMonthValue()) {
                continue;
            }
            int hour = reading.start().getHour();
            if (hour >= 23 || hour < 7) {
                // night zone
                for (int k = 0; k < reading.kilowattHours(); k++) {
                    consumed = consumed + 1;
                    long price = 320;
                    if (consumed <= 150) {
                        price = price * 80 / 100;
                    } else if (consumed > 600) {
                        price = price * 130 / 100;
                    }
                    energyCost = energyCost + price;
                }
                nightKwh = nightKwh + reading.kilowattHours();
            } else {
                // day zone
                for (int k = 0; k < reading.kilowattHours(); k++) {
                    consumed = consumed + 1;
                    long price = 640;
                    if (consumed <= 150) {
                        price = price * 80 / 100;
                    } else if (consumed > 600) {
                        price = price * 130 / 100;
                    }
                    energyCost = energyCost + price;
                }
                dayKwh = dayKwh + reading.kilowattHours();
            }
        }

        long ruralDiscount = 0;
        if (customer.rural()) {
            ruralDiscount = (energyCost * 30 + 50) / 100;
        }
        long serviceFee = 12000;
        long total = energyCost - ruralDiscount + serviceFee;
        return new ElectricityBill(dayKwh, nightKwh, energyCost, ruralDiscount, serviceFee, total);
    }
}
