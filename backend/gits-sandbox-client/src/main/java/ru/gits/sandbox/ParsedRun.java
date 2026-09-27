package ru.gits.sandbox;

import java.util.List;

/**
 * Sandbox output split by the entrypoint markers.
 *
 * @param compiled      false when the entrypoint reported a compilation error
 * @param compileOutput javac output for a compilation error, otherwise null
 * @param consoleOutput test console output (tree of tests and messages), may be empty
 * @param testCases     parsed JUnit report; empty when the report is missing (killed run, output flood)
 * @param reportPresent whether a JUnit report was produced
 */
public record ParsedRun(boolean compiled, String compileOutput, String consoleOutput,
                        List<TestCaseResult> testCases, boolean reportPresent) {
}
