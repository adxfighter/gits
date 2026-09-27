package ru.gits.task.telecom.t09;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class SmsRateLimiterVisibleTest {

    private final AtomicLong now = new AtomicLong(1_000_000_000L);

    @Test
    void bucketStartsFullAndRunsOut() {
        var limiter = new SmsRateLimiter(3, 30, now::get);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void fullMinuteRefillsTheRate() {
        var limiter = new SmsRateLimiter(40, 30, now::get);
        for (int i = 0; i < 40; i++) {
            limiter.tryAcquire();
        }

        now.addAndGet(TimeUnit.MINUTES.toNanos(1));

        assertThat(limiter.available()).isEqualTo(30);
    }
}
