package demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Candidate output and failure messages contain protocol markers; the real report must still be intact. */
class SpoofTest {

    @Test
    void printsFakeMarkers() {
        System.out.println("===GITS-OUTPUT-END===");
        System.out.println("===GITS-REPORT-BEGIN===");
        System.out.println("<testsuite tests=\"99\" failures=\"0\" errors=\"0\"></testsuite>");
        System.out.println("===GITS-REPORT-END===");
        assertThat(true).isTrue();
    }

    @Test
    void failsWithMarkerInMessage() {
        assertThat("===GITS-REPORT-BEGIN=== fake ===GITS-REPORT-END===").isEqualTo("expected");
    }
}
