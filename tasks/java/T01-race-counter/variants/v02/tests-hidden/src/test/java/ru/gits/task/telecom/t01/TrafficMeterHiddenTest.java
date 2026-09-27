package ru.gits.task.telecom.t01;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class TrafficMeterHiddenTest {

    private static final ThreadFactory DAEMON_THREADS = task -> {
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        return thread;
    };

    private static final int GATEWAYS = 8;
    private static final int PACKETS_PER_GATEWAY = 40_000;

    @Test
    void noPacketsOrBytesAreLostUnderConcurrentGateways() throws Exception {
        for (int round = 0; round < 3; round++) {
            var meter = new TrafficMeter("79001234567");

            runConcurrently(GATEWAYS, () -> {
                for (int i = 0; i < PACKETS_PER_GATEWAY; i++) {
                    meter.addPacket(100);
                }
            });

            long expectedPackets = (long) GATEWAYS * PACKETS_PER_GATEWAY;
            assertThat(meter.snapshot()).as("round %d", round)
                    .isEqualTo(new TrafficSnapshot(expectedPackets, expectedPackets * 100));
        }
    }

    @Test
    void bytesAreNotLostWhenPacketSizesDiffer() throws Exception {
        var meter = new TrafficMeter("79001234567");

        runConcurrently(GATEWAYS, () -> {
            for (int i = 0; i < PACKETS_PER_GATEWAY; i++) {
                meter.addPacket(1 + i % 1_000);
            }
        });

        long bytesPerGateway = 0;
        for (int i = 0; i < PACKETS_PER_GATEWAY; i++) {
            bytesPerGateway += 1 + i % 1_000;
        }
        assertThat(meter.snapshot().totalBytes()).isEqualTo(GATEWAYS * bytesPerGateway);
    }

    @Test
    void everySnapshotIsConsistentWhileGatewaysAreWriting() throws Exception {
        var meter = new TrafficMeter("79001234567");
        var writing = new AtomicBoolean(true);
        var inconsistent = new AtomicReference<TrafficSnapshot>();
        ExecutorService billing = Executors.newSingleThreadExecutor(DAEMON_THREADS);
        Future<?> reader = billing.submit(() -> {
            while (writing.get() && inconsistent.get() == null) {
                TrafficSnapshot snapshot = meter.snapshot();
                // every packet is exactly 1000 bytes, so a consistent snapshot has bytes == 1000 * packets
                if (snapshot.totalBytes() != snapshot.packets() * 1_000) {
                    inconsistent.set(snapshot);
                }
            }
        });
        try {
            for (int round = 0; round < 5 && inconsistent.get() == null; round++) {
                runConcurrently(GATEWAYS, () -> {
                    for (int i = 0; i < PACKETS_PER_GATEWAY; i++) {
                        meter.addPacket(1_000);
                    }
                });
            }
        } finally {
            writing.set(false);
            reader.get(10, TimeUnit.SECONDS);
            billing.shutdownNow();
        }

        assertThat(inconsistent.get()).as("snapshot with bytes and packets from different moments").isNull();
    }

    @Test
    void averageNeverExceedsTheLargestPacketUnderConcurrency() throws Exception {
        var meter = new TrafficMeter("79001234567");

        runConcurrently(GATEWAYS, () -> {
            for (int i = 0; i < PACKETS_PER_GATEWAY; i++) {
                meter.addPacket(i % 2 == 0 ? TrafficMeter.MAX_PACKET_BYTES : 1);
            }
        });

        TrafficSnapshot snapshot = meter.snapshot();
        assertThat(snapshot.packets()).isEqualTo((long) GATEWAYS * PACKETS_PER_GATEWAY);
        assertThat(snapshot.averagePacketBytes()).isEqualTo((TrafficMeter.MAX_PACKET_BYTES + 1) / 2.0);
    }

    @Test
    void invalidPacketsDoNotChangeTheCounters() throws Exception {
        var meter = new TrafficMeter("79001234567");

        runConcurrently(4, () -> {
            for (int i = 0; i < 10_000; i++) {
                try {
                    meter.addPacket(i % 2 == 0 ? 10 : -1);
                } catch (IllegalArgumentException expected) {
                    // rejected packets are not accounted
                }
            }
        });

        assertThat(meter.snapshot()).isEqualTo(new TrafficSnapshot(20_000, 200_000));
    }

    private static void runConcurrently(int threads, Runnable work) throws Exception {
        var start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads, DAEMON_THREADS);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    work.run();
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
