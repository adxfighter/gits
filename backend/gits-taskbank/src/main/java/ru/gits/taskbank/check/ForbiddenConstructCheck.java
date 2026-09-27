package ru.gits.taskbank.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

/**
 * AST check of task sources (starter, solution, tests): tasks must not need the network, the file
 * system, processes, reflection on tests or native code, and tests must not sleep for long.
 */
public final class ForbiddenConstructCheck {

    private static final long MAX_TEST_SLEEP_MS = 200;

    private static final List<String> FORBIDDEN_PACKAGES = List.of(
            "java.net", "java.nio.file", "java.nio.channels", "java.lang.reflect", "java.lang.invoke",
            "sun.", "jdk.internal", "javax.net", "java.rmi");

    private static final Set<String> FORBIDDEN_TYPES = Set.of(
            "Socket", "ServerSocket", "DatagramSocket", "URL", "URLConnection", "HttpURLConnection", "HttpClient",
            "ProcessBuilder", "ProcessHandle", "File", "Files", "FileInputStream", "FileOutputStream", "FileReader",
            "FileWriter", "RandomAccessFile", "Unsafe");

    private static final Set<String> REFLECTION_METHODS = Set.of(
            "getDeclaredField", "getDeclaredFields", "getDeclaredMethod", "getDeclaredMethods",
            "getDeclaredConstructor", "getDeclaredConstructors", "setAccessible");

    private static final Map<String, Long> TIME_UNIT_MS = Map.of(
            "NANOSECONDS", 0L, "MICROSECONDS", 0L, "MILLISECONDS", 1L, "SECONDS", 1_000L,
            "MINUTES", 60_000L, "HOURS", 3_600_000L, "DAYS", 86_400_000L);

    private final JavaParser parser = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));

    /** Violations as "path: description"; empty when the file is acceptable. */
    public List<String> check(String path, String source, boolean testSource) {
        List<String> violations = new ArrayList<>();
        ParseResult<CompilationUnit> parsed = parser.parse(source);
        if (!parsed.isSuccessful() || parsed.getResult().isEmpty()) {
            violations.add(path + ": не разбирается как Java 21 — " + parsed.getProblems());
            return violations;
        }
        CompilationUnit unit = parsed.getResult().get();

        for (ImportDeclaration imported : unit.getImports()) {
            String name = imported.getNameAsString();
            FORBIDDEN_PACKAGES.stream().filter(name::startsWith).findFirst()
                    .ifPresent(pkg -> violations.add(path + ": импорт из запрещённого пакета " + name));
        }
        for (ClassOrInterfaceType type : unit.findAll(ClassOrInterfaceType.class)) {
            String full = type.getNameWithScope();
            if (FORBIDDEN_TYPES.contains(type.getNameAsString())
                    || FORBIDDEN_PACKAGES.stream().anyMatch(full::startsWith)) {
                violations.add(path + ": запрещённый тип " + full);
            }
        }
        for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
            String name = call.getNameAsString();
            String scope = call.getScope().map(Expression::toString).orElse("");
            if (scope.equals("System") && (name.equals("exit") || name.startsWith("load"))) {
                violations.add(path + ": вызов System." + name);
            } else if (scope.contains("Runtime") && (name.equals("exec") || name.equals("halt") || name.equals("exit"))) {
                violations.add(path + ": вызов Runtime." + name);
            } else if (REFLECTION_METHODS.contains(name) || (name.equals("forName") && scope.equals("Class"))) {
                violations.add(path + ": рефлексия (" + name + ")");
            } else if (FORBIDDEN_PACKAGES.stream().anyMatch(scope::startsWith)) {
                violations.add(path + ": вызов из запрещённого пакета " + scope + "." + name);
            } else if (testSource && name.equals("sleep")) {
                checkSleep(path, call, scope).ifPresent(violations::add);
            }
        }
        return violations;
    }

    private static java.util.Optional<String> checkSleep(String path, MethodCallExpr call, String scope) {
        if (call.getArguments().isEmpty()) {
            return java.util.Optional.empty();
        }
        long factor = 1;
        if (scope.startsWith("TimeUnit.")) {
            factor = TIME_UNIT_MS.getOrDefault(scope.substring("TimeUnit.".length()), 1L);
        } else if (!scope.equals("Thread")) {
            return java.util.Optional.empty();
        }
        Expression argument = call.getArgument(0);
        Long value = null;
        if (argument instanceof IntegerLiteralExpr literal) {
            value = literal.asNumber().longValue();
        } else if (argument instanceof LongLiteralExpr literal) {
            value = literal.asNumber().longValue();
        }
        if (value == null) {
            return java.util.Optional.of(path + ": " + call + " — длительность паузы в тестах должна быть литералом");
        }
        long millis = value * factor;
        return millis > MAX_TEST_SLEEP_MS
                ? java.util.Optional.of(path + ": пауза " + millis + " мс в тесте (не более " + MAX_TEST_SLEEP_MS + ")")
                : java.util.Optional.empty();
    }
}
