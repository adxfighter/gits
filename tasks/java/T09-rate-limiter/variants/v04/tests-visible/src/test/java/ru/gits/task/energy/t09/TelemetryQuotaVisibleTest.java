package ru.gits.task.energy.t09;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class TelemetryQuotaVisibleTest {

    private final AtomicLong now = new AtomicLong();

    @Test
    void batchesWithinTheQuotaAreAccepted() {
        var quota = new TelemetryQuota(10, 1, now::get);

        assertThat(quota.tryAcquire("meter-1", 4)).isTrue();
        assertThat(quota.tryAcquire("meter-1", 6)).isTrue();
        assertThat(quota.available("meter-1")).isZero();
        assertThat(quota.available("meter-2")).isEqualTo(10);
    }

    @Test
    void quotaIsRefilled() {
        var quota = new TelemetryQuota(10, 5, now::get);
        assertThat(quota.tryAcquire("meter-1", 10)).isTrue();

        now.addAndGet(1_000_000_000L);

        assertThat(quota.tryAcquire("meter-1", 5)).isTrue();
    }
}
