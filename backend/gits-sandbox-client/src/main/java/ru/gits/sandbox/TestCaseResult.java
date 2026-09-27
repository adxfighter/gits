package ru.gits.sandbox;

/**
 * One test case from the JUnit XML report.
 *
 * @param className fully qualified test class, e.g. demo.CalcTest or demo.CalcTest$Nested
 * @param name      display name, e.g. {@code sumAddsValues()} or {@code max(int, int)[1]}
 * @param status    outcome
 * @param message   failure/error message (may be null), truncated to 2 KB
 */
public record TestCaseResult(String className, String name, Status status, String message) {

    public enum Status {
        PASSED,
        FAILED,
        ERROR,
        SKIPPED
    }

    public static final int MAX_MESSAGE = 2048;

    /** Method name without the parameter list and invocation index. */
    public String methodName() {
        int paren = name.indexOf('(');
        return paren < 0 ? name : name.substring(0, paren);
    }

    public boolean passed() {
        return status == Status.PASSED;
    }
}
