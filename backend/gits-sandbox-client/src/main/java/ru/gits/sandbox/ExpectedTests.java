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
     * A test annotation, then any further annotations, modifiers, an optional type parameter list and the
     * return type, then the name. Applied to {@link #simplify simplified} code only: without comments,
     * literals and annotation arguments, so no repetition here has to walk long argument lists.
     */
    private static final Pattern TEST_METHOD = Pattern.compile(
            "@(?:org\\.junit\\.jupiter\\.api\\.)?(?:Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\\b"
                    + "(?:\\s*+@[\\w.]++)*+"
                    + "\\s*+(?:(?:public|protected|private|static|final|synchronized)\\s++)*+"
                    + "(?:<[^>]*+>\\s*+)?"
                    + "[\\w.<>\\[\\],?]++(?:\\s*+<[^>]*+>)?\\s++"
                    + "(\\w++)\\s*+\\(");
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

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
            String code = simplify(source.content());
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

    /**
     * Drops comments, empties string, char and text block literals and removes annotation argument lists
     * ({@code @CsvSource({...})} becomes {@code @CsvSource}). A single linear pass instead of a regex:
     * long {@code @CsvSource} tables overflowed the stack of a backtracking pattern.
     */
    static String simplify(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int depth = 0; // parentheses depth inside a skipped annotation argument list
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (source.startsWith("//", i)) {
                i = indexOrEnd(source, "\n", i);
            } else if (source.startsWith("/*", i)) {
                i = indexOrEnd(source, "*/", i + 2) + 2;
                out.append(' ');
            } else if (source.startsWith("\"\"\"", i)) {
                i = literalEnd(source, i + 3, "\"\"\"");
                out.append(depth > 0 ? "" : "\"\"");
            } else if (c == '"' || c == '\'') {
                i = literalEnd(source, i + 1, String.valueOf(c));
                out.append(depth > 0 ? "" : c + "" + c);
            } else if (depth > 0) {
                depth += c == '(' ? 1 : c == ')' ? -1 : 0;
                i++;
            } else if (c == '@') {
                int end = i + 1;
                while (end < source.length()
                        && (Character.isJavaIdentifierPart(source.charAt(end)) || source.charAt(end) == '.')) {
                    end++;
                }
                out.append(source, i, end);
                i = end;
                while (end < source.length() && Character.isWhitespace(source.charAt(end))) {
                    end++;
                }
                if (end < source.length() && source.charAt(end) == '(') {
                    depth = 1;
                    i = end + 1;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int indexOrEnd(String source, String token, int from) {
        int index = source.indexOf(token, from);
        return index < 0 ? source.length() : index;
    }

    /** The index after the closing quote of a literal whose content starts at {@code from}. */
    private static int literalEnd(String source, int from, String quote) {
        int i = from;
        while (i < source.length() && !source.startsWith(quote, i)) {
            i += source.charAt(i) == '\\' ? 2 : 1;
        }
        return Math.min(i + quote.length(), source.length());
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
