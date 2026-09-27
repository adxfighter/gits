package ru.gits.taskbank.check;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ForbiddenConstructCheckTest {

    private final ForbiddenConstructCheck check = new ForbiddenConstructCheck();

    private static String wrap(String imports, String body) {
        return imports + "\nclass A { void m() throws Exception { " + body + " } }";
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "System.exit(1);", "Runtime.getRuntime().exec(\"ls\");", "new ProcessBuilder(\"ls\");",
            "new java.net.Socket(\"h\", 1);", "java.net.http.HttpClient.newHttpClient();", "new java.io.File(\"x\");",
            "java.nio.file.Files.readString(null);", "Class.forName(\"X\");", "A.class.getDeclaredField(\"f\");",
            "System.loadLibrary(\"x\");"})
    void rejectsForbiddenCallsAndTypes(String body) {
        assertThat(check.check("A.java", wrap("", body), false)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"import java.net.URI;", "import java.nio.file.Path;", "import java.lang.reflect.Field;"})
    void rejectsForbiddenImports(String imports) {
        assertThat(check.check("A.java", wrap(imports, ""), false)).isNotEmpty();
    }

    @Test
    void limitsSleepInTests() {
        assertThat(check.check("T.java", wrap("", "Thread.sleep(150);"), true)).isEmpty();
        assertThat(check.check("T.java", wrap("", "Thread.sleep(500);"), true)).singleElement()
                .satisfies(v -> assertThat(v).contains("500"));
        assertThat(check.check("T.java", wrap("import java.util.concurrent.TimeUnit;", "TimeUnit.SECONDS.sleep(1);"), true))
                .isNotEmpty();
        assertThat(check.check("T.java", wrap("", "long d = 10; Thread.sleep(d);"), true)).isNotEmpty();
        // Main code may sleep (e.g. simulated latency); only tests are limited
        assertThat(check.check("A.java", wrap("", "Thread.sleep(500);"), false)).isEmpty();
    }

    @Test
    void acceptsOrdinaryCode() {
        String code = """
                import java.util.*;
                import java.util.concurrent.*;
                import java.nio.charset.Charset;
                class A {
                    private final Map<String, Integer> totals = new ConcurrentHashMap<>();
                    void m() { Charset.forName("UTF-8"); new Thread(() -> {}).start(); System.out.println(totals); }
                }
                """;

        assertThat(check.check("A.java", code, false)).isEmpty();
    }

    @Test
    void reportsUnparsableSource() {
        assertThat(check.check("A.java", "class A { void m( }", false)).singleElement()
                .satisfies(v -> assertThat(v).contains("не разбирается"));
    }
}
