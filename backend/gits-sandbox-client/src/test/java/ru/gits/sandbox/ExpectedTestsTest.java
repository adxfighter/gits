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

    @Test
    void survivesLargeCsvSource() {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            rows.append("            \"customer-").append(i).append(", 2026-09-").append(10 + i)
                    .append(", PREMIUM, 12345.67, expected discount for a loyal customer\",\n");
        }
        String source = "package demo;\nclass CsvTest {\n    @ParameterizedTest\n    @CsvSource({\n" + rows
                + "    })\n    void discount(String id, String date, String tier, String sum, String note) {}\n}\n";

        ExpectedTests csv = ExpectedTests.of(List.of(new SourceFile("src/test/java/demo/CsvTest.java", source)));

        assertThat(csv.methods()).extracting(TestMethod::method).containsExactly("discount");
    }

    @Test
    void survivesLongAnnotationArguments() {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            rows.append("            \"order-").append(i).append(", (amount ").append(i * 100)
                    .append("), note with ) and // not a comment\",\n");
        }
        String source = """
                package demo;

                class LongCsvTest {

                    @ParameterizedTest(name = "{index}: {0}")
                    @CsvSource(delimiter = ',', value = {
                """ + rows + """
                    })
                    @Timeout(value = 5, unit = java.util.concurrent.TimeUnit.SECONDS)
                    void priced(String id, String amount, String note) {}

                    @ParameterizedTest
                    @CsvSource(textBlock = \"""
                            first,  (1
                            second, 2) // still text
                            \""")
                    void textBlock(String name, String value) {}

                    @Test
                    @DisplayName("char ')' and nested @Tag(\\"x\\") /* text */")
                    void tricky() { char c = '('; }
                }
                """;

        ExpectedTests longSource =
                ExpectedTests.of(List.of(new SourceFile("src/test/java/demo/LongCsvTest.java", source)));

        assertThat(longSource.methods()).extracting(TestMethod::method)
                .containsExactlyInAnyOrder("priced", "textBlock", "tricky");
    }

    private static TestCaseResult ok(String className, String name) {
        return new TestCaseResult(className, name, Status.PASSED, null);
    }
}
