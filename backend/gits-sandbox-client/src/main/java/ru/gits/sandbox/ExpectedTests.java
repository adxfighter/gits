package ru.gits.sandbox;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test methods declared by trusted test sources (task files, never candidate files), used to check that a
 * JUnit report really comes from these tests. Candidate code runs in the same JVM as JUnit and could write
 * a forged report; a forged report must at least reproduce the exact set of test methods.
 */
public final class ExpectedTests {

    /**
     * A test annotation (with optional arguments), then any further annotations with arguments one level
     * of parentheses deep, modifiers, an optional type parameter list and the return type, then the name.
     */
    private static final Pattern TEST_METHOD = Pattern.compile(
            "@(?:org\\.junit\\.jupiter\\.api\\.)?(?:Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\\b"
                    + "(?:\\s*\\((?:[^()]|\\([^()]*\\))*\\))?"
                    + "(?:\\s*@[\\w.]+(?:\\s*\\((?:[^()]|\\([^()]*\\))*\\))?)*"
                    + "\\s*(?:(?:public|protected|private|static|final|synchronized)\\s+)*"
                    + "(?:<[^>]*>\\s*)?"
                    + "[\\w.<>\\[\\],?]+(?:\\s*<[^>]*>)?\\s+"
                    + "(\\w+)\\s*\\(");
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    /** A test method of a top-level test class (nested classes report as {@code Outer$Inner}). */
    public record TestMethod(String className, String method) {
    }

    private final Set<String> classes = new HashSet<>();
    private final Set<TestMethod> methods = new HashSet<>();

    private ExpectedTests() {
    }

    public static ExpectedTests of(List<SourceFile> testSources) {
        var expected = new ExpectedTests();
        for (SourceFile source : testSources) {
            String code = BLOCK_COMMENT.matcher(LINE_COMMENT.matcher(source.content()).replaceAll("")).replaceAll("");
            String className = className(source.path(), code);
            expected.classes.add(className);
            Matcher matcher = TEST_METHOD.matcher(code);
            while (matcher.find()) {
                expected.methods.add(new TestMethod(className, matcher.group(1)));
            }
        }
        return expected;
    }

    public Set<TestMethod> methods() {
        return Set.copyOf(methods);
    }

    public boolean declaresClass(String reportedClassName) {
        return classes.contains(topLevel(reportedClassName));
    }

    /**
     * The report is consistent when every reported test case belongs to a known test class and every
     * declared test method appears at least once.
     */
    public boolean matches(List<TestCaseResult> cases) {
        if (cases.stream().anyMatch(c -> !declaresClass(c.className()))) {
            return false;
        }
        Set<TestMethod> reported = new HashSet<>();
        for (TestCaseResult testCase : cases) {
            reported.add(new TestMethod(topLevel(testCase.className()), testCase.methodName()));
        }
        return reported.containsAll(methods);
    }

    private static String topLevel(String className) {
        int nested = className.indexOf('$');
        return nested < 0 ? className : className.substring(0, nested);
    }

    private static String className(String path, String code) {
        String file = path.substring(path.lastIndexOf('/') + 1).replace(".java", "");
        Matcher pkg = PACKAGE.matcher(code);
        return pkg.find() ? pkg.group(1) + "." + file : file;
    }
}
