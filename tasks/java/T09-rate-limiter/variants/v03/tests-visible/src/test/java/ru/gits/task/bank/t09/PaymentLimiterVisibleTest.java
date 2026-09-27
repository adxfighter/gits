package ru.gits.task.bank.t09;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class PaymentLimiterVisibleTest {

    private final AtomicLong now = new AtomicLong();

    @Test
    void capacityIsGrantedThenRefused() {
        var limiter = new PaymentLimiter(3, 1, now::get);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
        assertThat(limiter.available()).isZero();
    }

    @Test
    void tokensAreRefilledWithFractions() {
        var limiter = new PaymentLimiter(10, 4, now::get);
        while (limiter.tryAcquire()) {
            // drain
        }

        now.addAndGet(600_000_000L);   // 2.4 tokens
        assertThat(limiter.available()).isEqualTo(2);
        now.addAndGet(150_000_000L);   // +0.6
        assertThat(limiter.available()).isEqualTo(3);
    }
}
