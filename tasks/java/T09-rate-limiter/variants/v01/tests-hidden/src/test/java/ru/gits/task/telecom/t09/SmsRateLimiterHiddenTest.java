package ru.gits.task.telecom.t09;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class SmsRateLimiterHiddenTest {

    private final AtomicLong now = new AtomicLong(5_000_000_000L);

    private static void drain(SmsRateLimiter limiter) {
        while (limiter.tryAcquire()) {
            // take everything
        }
    }

    @Test
    void frequentRequestsAccumulateFractions() {
        var limiter = new SmsRateLimiter(30, 30, now::get);
        drain(limiter);

        int sent = 0;
        for (int i = 0; i < 41; i++) {  // a request every 1.5 s for 61.5 s: 30.75 tokens
            now.addAndGet(1_500_000_000L);
            if (limiter.tryAcquire()) {
                sent++;
            }
        }

        assertThat(sent).isEqualTo(30);
    }

    @Test
    void halfATokenPerSecond() {
        var limiter = new SmsRateLimiter(5, 30, now::get);
        drain(limiter);

        now.addAndGet(TimeUnit.MILLISECONDS.toNanos(1_500));
        assertThat(limiter.tryAcquire()).isFalse();
        now.addAndGet(TimeUnit.MILLISECONDS.toNanos(600));
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void slowRateIsNotRoundedDown() {
        var limiter = new SmsRateLimiter(10, 7, now::get);
        drain(limiter);

        for (int i = 0; i < 610; i++) {  // polled every 100 ms for 61 s: 7.1 tokens
            now.addAndGet(TimeUnit.MILLISECONDS.toNanos(100));
            limiter.available();
        }

        assertThat(limiter.available()).isEqualTo(7);
    }

    @Test
    void refillNeverExceedsCapacity() {
        var limiter = new SmsRateLimiter(5, 600, now::get);
        drain(limiter);

        now.addAndGet(TimeUnit.MINUTES.toNanos(3));

        assertThat(limiter.available()).isEqualTo(5);
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire()).isTrue();
        }
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void veryLongIdlePeriodRefillsToCapacity() {
        var limiter = new SmsRateLimiter(100, 6_000, now::get);
        drain(limiter);

        now.addAndGet(TimeUnit.DAYS.toNanos(400));

        assertThat(limiter.available()).isEqualTo(100);
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.available()).isEqualTo(99);
    }

    @Test
    void remainderIsKeptAfterATokenIsTaken() {
        var limiter = new SmsRateLimiter(3, 60, now::get);  // one token per second
        drain(limiter);

        now.addAndGet(TimeUnit.MILLISECONDS.toNanos(1_700));
        assertThat(limiter.tryAcquire()).isTrue();
        now.addAndGet(TimeUnit.MILLISECONDS.toNanos(350));
        assertThat(limiter.tryAcquire()).as("0.7 left + 0.35 new").isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }
}
