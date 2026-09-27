package ru.gits.runner;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import ru.gits.sandbox.SourceFile;

/**
 * Static check of candidate files for constructs that could end the test JVM early or tamper with the
 * test report (candidate code runs in the same JVM as JUnit). This raises the bar but is not a proof of
 * safety: the report is additionally checked against the expected tests (see ExpectedTests).
 */
final class ForbiddenApiScanner {

    private static final Map<String, Pattern> RULES = new LinkedHashMap<>();

    static {
        RULES.put("System.exit", Pattern.compile("\\bSystem\\s*\\.\\s*exit\\s*\\("));
        RULES.put("Runtime.halt / Runtime.exec", Pattern.compile("\\bRuntime\\b[^;]*\\.\\s*(halt|exec|exit)\\s*\\("));
        RULES.put("ProcessBuilder", Pattern.compile("\\bProcessBuilder\\b"));
        RULES.put("ProcessHandle", Pattern.compile("\\bProcessHandle\\b"));
        RULES.put("запись файлов", Pattern.compile(
                "\\bjava\\.nio\\.file\\b|\\bFiles\\s*\\.|\\bFileOutputStream\\b|\\bFileWriter\\b|\\bRandomAccessFile\\b|\\bPrintWriter\\s*\\(\\s*\""));
        RULES.put("пути файловой системы песочницы", Pattern.compile("\"[^\"\\n]*(/work|/tmp|/opt|TEST-junit)[^\"\\n]*\""));
        RULES.put("рефлексия setAccessible", Pattern.compile("\\bsetAccessible\\s*\\("));
        RULES.put("sun.misc.Unsafe", Pattern.compile("\\bUnsafe\\b"));
        RULES.put("внутренние API JDK", Pattern.compile("\\bjdk\\.internal\\b|\\bsun\\.(misc|nio)\\b"));
        RULES.put("загрузка нативного кода", Pattern.compile("\\bSystem\\s*\\.\\s*(load|loadLibrary)\\s*\\("));
    }

    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    private ForbiddenApiScanner() {
    }

    /** Human-readable description of the first violation, or empty when the files are acceptable. */
    static Optional<String> findViolation(List<SourceFile> candidateFiles) {
        for (SourceFile file : candidateFiles) {
            String code = BLOCK_COMMENT.matcher(LINE_COMMENT.matcher(file.content()).replaceAll("")).replaceAll("");
            for (var rule : RULES.entrySet()) {
                if (rule.getValue().matcher(code).find()) {
                    return Optional.of(file.path() + ": использование запрещено в задачах оценки — " + rule.getKey());
                }
            }
        }
        return Optional.empty();
    }
}
