package demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Passes only when allocating 2 GB is impossible inside the sandbox. */
class MemoryBombTest {

    @Test
    void cannotAllocateTwoGigabytes() {
        List<byte[]> chunks = new ArrayList<>();
        Throwable failure = null;
        try {
            for (int i = 0; i < 32; i++) {
                chunks.add(new byte[64 * 1024 * 1024]);
            }
        } catch (OutOfMemoryError e) {
            failure = e;
        }
        int allocatedMb = chunks.size() * 64;
        chunks.clear();
        assertThat(failure).as("2 GB allocation must fail, got " + allocatedMb + " MB").isNotNull();
        assertThat(allocatedMb).isLessThan(768);
    }
}
