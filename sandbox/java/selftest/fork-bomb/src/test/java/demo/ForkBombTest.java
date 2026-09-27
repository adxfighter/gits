package demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;

/** Passes only when the pids limit stops unbounded thread and process creation early. */
class ForkBombTest {

    @Test
    void threadCreationHitsPidsLimit() throws Exception {
        var release = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        Throwable failure = null;
        try {
            for (int i = 0; i < 100_000; i++) {
                Thread thread = new Thread(() -> {
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                thread.setDaemon(true);
                thread.start();
                threads.add(thread);
            }
        } catch (OutOfMemoryError e) {
            failure = e;
        } finally {
            release.countDown();
        }
        assertThat(failure).as("thread creation must be stopped by the pids limit").isNotNull();
        assertThat(threads.size()).isLessThan(200);
    }

    @Test
    void processCreationIsLimited() {
        List<Process> processes = new ArrayList<>();
        Throwable failure = null;
        try {
            for (int i = 0; i < 10_000; i++) {
                processes.add(new ProcessBuilder("/bin/sleep", "30").start());
            }
        } catch (Throwable e) {
            failure = e;
        } finally {
            processes.forEach(Process::destroyForcibly);
        }
        assertThat(failure).as("process creation must be stopped by the pids limit").isNotNull();
        assertThat(processes.size()).isLessThan(200);
    }
}
