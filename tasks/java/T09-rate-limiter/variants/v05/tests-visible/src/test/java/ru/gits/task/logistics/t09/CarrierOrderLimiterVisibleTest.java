package ru.gits.task.logistics.t09;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class CarrierOrderLimiterVisibleTest {

    private final AtomicLong now = new AtomicLong();

    @Test
    void ordersWithinTheLimit() {
        var limiter = new CarrierOrderLimiter(20, 2, now::get);

        assertThat(limiter.tryAcquire("cdek", 12)).isTrue();
        assertThat(limiter.tryAcquire("cdek", 10)).isFalse();
        assertThat(limiter.tryAcquire("cdek", 8)).isTrue();
        assertThat(limiter.tryAcquire("pek", 20)).isTrue();
    }

    @Test
    void retryAfterForAnAvailableOrderIsZero() {
        var limiter = new CarrierOrderLimiter(20, 2, now::get);

        assertThat(limiter.retryAfterNanos("cdek", 5)).isZero();
        assertThat(limiter.tryAcquire("cdek", 20)).isTrue();
        assertThat(limiter.retryAfterNanos("cdek", 2)).isEqualTo(1_000_000_000L);
    }
}
