package demo;

import org.junit.jupiter.api.Test;

/** Prints without end; the sandbox must cap the output and end the run instead of filling memory or disk. */
class OutputFloodTest {

    @Test
    void floodsStdout() {
        String line = "x".repeat(1000);
        while (true) {
            System.out.println(line);
        }
    }
}
