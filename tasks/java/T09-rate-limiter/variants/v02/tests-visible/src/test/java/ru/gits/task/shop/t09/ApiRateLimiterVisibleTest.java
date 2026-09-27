package ru.gits.task.shop.t09;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class ApiRateLimiterVisibleTest {

    private final AtomicLong now = new AtomicLong();

    @Test
    void eachClientHasItsOwnLimit() {
        var limiter = new ApiRateLimiter(2, 1, now::get);

        assertThat(limiter.tryAcquire("acme")).isTrue();
        assertThat(limiter.tryAcquire("acme")).isTrue();
        assertThat(limiter.tryAcquire("acme")).isFalse();
        assertThat(limiter.tryAcquire("globex")).isTrue();
    }

    @Test
    void limitIsRestoredOverTime() {
        var limiter = new ApiRateLimiter(1, 1, now::get);
        assertThat(limiter.tryAcquire("acme")).isTrue();
        assertThat(limiter.tryAcquire("acme")).isFalse();

        now.addAndGet(1_000_000_000L);

        assertThat(limiter.tryAcquire("acme")).isTrue();
    }
}
