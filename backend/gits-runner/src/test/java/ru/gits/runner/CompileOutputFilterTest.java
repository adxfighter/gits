package ru.gits.runner;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class CompileOutputFilterTest {

    @Test
    void keepsOnlyDiagnosticsForCandidateFiles() {
        String javac = """
                /work/src/src/main/java/demo/Sum.java:4: error: ';' expected
                    int total = 0
                                 ^
                /work/src/src/test/java/demo/SumHiddenTest.java:5: error: cannot find symbol
                    @Test void secret() { assertThat(Sum.of(new int[] {5})).isEqualTo(5); }
                                                        ^
                2 errors
                """;

        String filtered = RunJobProcessor.onlyCandidateErrors(javac, Set.of("src/main/java/demo/Sum.java"));

        assertThat(filtered).startsWith("src/main/java/demo/Sum.java:4: error: ';' expected")
                .doesNotContain("SumHiddenTest").doesNotContain("secret").doesNotContain("/work/src");
    }

    @Test
    void genericMessageWhenOnlyTestsFailToCompile() {
        String javac = "/work/src/src/test/java/demo/SumHiddenTest.java:5: error: cannot find symbol\n  code\n1 error\n";

        assertThat(RunJobProcessor.onlyCandidateErrors(javac, Set.of("src/main/java/demo/Sum.java")))
                .contains("не компилируется вместе с тестами проверки");
    }
}
