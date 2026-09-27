package ru.gits.runner;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ru.gits.sandbox.SourceFile;

class ForbiddenApiScannerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "System.exit(0);", "System . exit (1);", "Runtime.getRuntime().halt(0);", "Runtime.getRuntime().exec(\"sh\");",
            "new ProcessBuilder(\"sh\").start();", "ProcessHandle.current();", "java.nio.file.Files.writeString(p, s);",
            "Files.write(p, b);", "new FileOutputStream(\"x\");", "String p = \"/work/reports/TEST-junit-jupiter.xml\";",
            "field.setAccessible(true);", "sun.misc.Unsafe u;", "System.loadLibrary(\"x\");"})
    void flagsDangerousCode(String statement) {
        assertThat(ForbiddenApiScanner.findViolation(List.of(file(statement)))).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "executor.execute(task);", "List<SalesReport> reports = new ArrayList<>();", "String s = \"workers\";",
            "// System.exit(0);", "/* new ProcessBuilder() */ int x = 1;", "Map<String, Integer> totals = new HashMap<>();"})
    void acceptsOrdinaryCode(String statement) {
        assertThat(ForbiddenApiScanner.findViolation(List.of(file(statement)))).isEmpty();
    }

    private static SourceFile file(String statement) {
        return new SourceFile("src/main/java/demo/A.java", "package demo; class A { void m() { " + statement + " } }");
    }
}
