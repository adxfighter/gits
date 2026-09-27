package ru.gits.taskbank.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class LeakCheckTest {

    private static final String STARTER = """
            class Counter {
                private int value;
                void increment() {
                    value++;
                }
            }
            """;
    private static final String SOLUTION = """
            class Counter {
                private final AtomicInteger value = new AtomicInteger();
                void increment() {
                    value.incrementAndGet();
                }
                int get() {
                    return value.get();
                }
            }
            """;

    @Test
    void detectsCopiedSolutionFragment() {
        String test = """
                @Test void copies() {
                    private final AtomicInteger value = new AtomicInteger();
                      void increment() {
                          value.incrementAndGet();
                      int get() {
                }
                """;

        assertThat(LeakCheck.findLeak(Map.of("C.java", STARTER), Map.of("C.java", SOLUTION), Map.of("T.java", test)))
                .isPresent();
    }

    @Test
    void acceptsTestsThatOnlyUseTheApi() {
        String test = """
                @Test void increments() {
                    var counter = new Counter();
                    counter.increment();
                    assertThat(counter.get()).isEqualTo(1);
                }
                """;

        assertThat(LeakCheck.findLeak(Map.of("C.java", STARTER), Map.of("C.java", SOLUTION), Map.of("T.java", test)))
                .isEmpty();
    }

    @Test
    void starterCodeRepeatedInTestsIsNotALeak() {
        String test = "class Counter {\nprivate int value;\nvoid increment() {\nvalue++;\n}\n}";

        assertThat(LeakCheck.findLeak(Map.of("C.java", STARTER), Map.of("C.java", STARTER), Map.of("T.java", test)))
                .isEmpty();
    }
}
