package demo;

import org.junit.jupiter.api.Test;

/** Never finishes; the caller must kill the container on timeout. */
class LoopTest {

    @Test
    void spinsForever() {
        long counter = 0;
        while (counter >= 0) {
            counter = (counter + 1) % 1_000_000;
        }
    }
}
