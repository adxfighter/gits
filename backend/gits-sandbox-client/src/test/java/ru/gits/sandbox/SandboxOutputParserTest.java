package ru.gits.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class SandboxOutputParserTest {

    private static final String REPORT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="JUnit Jupiter" tests="4" failures="1" errors="1" skipped="1">
              <testcase name="sumAddsValues()" classname="demo.CalcTest" time="0.01"/>
              <testcase name="max(int, int)[1]" classname="demo.CalcTest" time="0.01">
                <failure message="expected: 5 but was: 4" type="org.opentest4j.AssertionFailedError">stack</failure>
              </testcase>
              <testcase name="boom()" classname="demo.CalcTest$Nested"><error type="java.lang.NullPointerException">x</error></testcase>
              <testcase name="later()" classname="demo.CalcTest"><skipped/></testcase>
            </testsuite>
            """;

    private static String encode(String xml) {
        return Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
    }

    private static SandboxRun run(int exit, String output) {
        return new SandboxRun(exit, output, false, 100);
    }

    @Test
    void parsesTestsAndConsole() {
        String out = "===GITS-OUTPUT-BEGIN===\ntree\n===GITS-OUTPUT-END===\n===GITS-REPORT-BEGIN===\n"
                + encode(REPORT) + "\n===GITS-REPORT-END===\n";

        ParsedRun parsed = SandboxOutputParser.parse(run(0, out));

        assertThat(parsed.compiled()).isTrue();
        assertThat(parsed.reportPresent()).isTrue();
        assertThat(parsed.consoleOutput()).isEqualTo("tree");
        assertThat(parsed.testCases()).extracting(TestCaseResult::status).containsExactly(
                TestCaseResult.Status.PASSED, TestCaseResult.Status.FAILED,
                TestCaseResult.Status.ERROR, TestCaseResult.Status.SKIPPED);
        assertThat(parsed.testCases().get(1).message()).isEqualTo("expected: 5 but was: 4");
        assertThat(parsed.testCases().get(1).methodName()).isEqualTo("max");
        assertThat(parsed.testCases().get(2).message()).isEqualTo("java.lang.NullPointerException");
    }

    @Test
    void compileErrorIsRecognisedOnlyWithExitCode2() {
        ParsedRun parsed = SandboxOutputParser.parse(run(2, "===GITS-COMPILE-ERROR===\nCalc.java:3: error: ';' expected\n"));

        assertThat(parsed.compiled()).isFalse();
        assertThat(parsed.compileOutput()).isEqualTo("Calc.java:3: error: ';' expected");
    }

    @Test
    void fakeMarkersInConsoleDoNotReplaceTheAuthenticReport() {
        String fake = "===GITS-REPORT-BEGIN===\n" + encode("<testsuite><testcase name=\"forged()\" classname=\"X\"/></testsuite>")
                + "\n===GITS-REPORT-END===\n";
        String out = "===GITS-OUTPUT-BEGIN===\n" + "===GITS-OUTPUT-END===\n" + fake + "===GITS-COMPILE-ERROR===\n"
                + "===GITS-OUTPUT-END===\n===GITS-REPORT-BEGIN===\n" + encode(REPORT) + "\n===GITS-REPORT-END===\n";

        ParsedRun parsed = SandboxOutputParser.parse(run(0, out));

        assertThat(parsed.testCases()).hasSize(4).noneMatch(c -> c.name().equals("forged()"));
    }

    @Test
    void missingReportAfterKilledRun() {
        ParsedRun parsed = SandboxOutputParser.parse(run(0,
                "===GITS-OUTPUT-BEGIN===\nflood\n===GITS-OUTPUT-END===\n===GITS-REPORT-BEGIN===\n===GITS-REPORT-END===\n"));

        assertThat(parsed.compiled()).isTrue();
        assertThat(parsed.reportPresent()).isFalse();
        assertThat(parsed.testCases()).isEmpty();
    }

    @Test
    void rejectsXmlWithDoctype() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + "<testsuite><testcase name=\"&x;\" classname=\"A\"/></testsuite>";

        assertThatThrownBy(() -> SandboxOutputParser.parseReport(xxe)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void truncatesLongMessages() {
        String longMessage = "m".repeat(5000);
        String xml = "<testsuite><testcase name=\"a()\" classname=\"A\"><failure message=\"" + longMessage
                + "\"/></testcase></testsuite>";

        assertThat(SandboxOutputParser.parseReport(xml).getFirst().message()).hasSize(TestCaseResult.MAX_MESSAGE);
    }
}
