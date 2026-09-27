package demo;

import org.junit.jupiter.api.Test;

class BrokenTest {

    @Test
    void neverRuns() {
        new Broken().value();
    }
}
