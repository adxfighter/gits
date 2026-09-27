package ru.gits.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import ru.gits.sandbox.ExpectedTests.TestMethod;
import ru.gits.sandbox.TestCaseResult.Status;

class ExpectedTestsTest {

    private static final String SOURCE = """
            package demo;

            import org.junit.jupiter.api.*;
            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.CsvSource;

            class CalcTest {

                @Test
                void sumAddsValues() {}

                @ParameterizedTest
                @CsvSource({"1,1", "5,(5)"})
                void max(int value, int expected) {}

                @RepeatedTest(3) @DisplayName("repeated")
                public final void repeated() {}

                @TestFactory
                java.util.stream.Stream<DynamicTest> dynamic() { return null; }

                // @Test void commentedOut() {}
                /* @Test void blockCommented() {} */

                void helper() {}

                @Nested
                class Inner {
                    @org.junit.jupiter.api.Test
                    void innerTest() {}
                }
            }
            """;

    private final ExpectedTests expected =
            ExpectedTests.of(List.of(new SourceFile("src/test/java/demo/CalcTest.java", SOURCE)));

    @Test
    void findsDeclaredTestMethodsOnly() {
        assertThat(expected.methods()).extracting(TestMethod::method)
                .containsExactlyInAnyOrder("sumAddsValues", "max", "repeated", "dynamic", "innerTest");
        assertThat(expected.methods()).allMatch(m -> m.className().equals("demo.CalcTest"));
    }

    @Test
    void matchesAConsistentReport() {
        List<TestCaseResult> report = List.of(
                ok("demo.CalcTest", "sumAddsValues()"), ok("demo.CalcTest", "max(int, int)[1]"),
                ok("demo.CalcTest", "max(int, int)[2]"), ok("demo.CalcTest", "repeated()"),
                ok("demo.CalcTest", "dynamic()"), ok("demo.CalcTest$Inner", "innerTest()"));

        assertThat(expected.matches(report)).isTrue();
    }

    @Test
    void rejectsReportMissingATest() {
        assertThat(expected.matches(List.of(ok("demo.CalcTest", "sumAddsValues()")))).isFalse();
    }

    @Test
    void rejectsReportWithUnknownClass() {
        List<TestCaseResult> report = List.of(
                ok("demo.CalcTest", "sumAddsValues()"), ok("demo.CalcTest", "max(int, int)[1]"),
                ok("demo.CalcTest", "repeated()"), ok("demo.CalcTest", "dynamic()"),
                ok("demo.CalcTest$Inner", "innerTest()"), ok("evil.Forged", "allGreen()"));

        assertThat(expected.matches(report)).isFalse();
    }

    private static TestCaseResult ok(String className, String name) {
        return new TestCaseResult(className, name, Status.PASSED, null);
    }
}
