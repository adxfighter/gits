package ru.gits.sandbox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

/** Builds the tar archive streamed to the sandbox, rejecting anything that is not a plain source file. */
public final class SourceArchive {

    public static final int MAX_FILES = 50;
    public static final int MAX_TOTAL_BYTES = 256 * 1024;

    /** Relative path under src/main/java or src/test/java, Java identifiers only, ending in .java. */
    private static final Pattern ALLOWED_PATH =
            Pattern.compile("src/(main|test)/java/([A-Za-z_$][A-Za-z0-9_$]*/)*[A-Za-z_$][A-Za-z0-9_$]*\\.java");

    private SourceArchive() {
    }

    /**
     * Java sources go to the sandbox; any other task file (a .txt text for the candidate, e.g. the warm-up sample and
     * the text retyped from it) is shown in the editor but never compiled, run or archived.
     */
    public static boolean isSource(String path) {
        return path.endsWith(".java");
    }

    public static byte[] build(List<SourceFile> files) {
        if (files.isEmpty()) {
            throw new InvalidSourceException("Нет файлов для запуска");
        }
        if (files.size() > MAX_FILES) {
            throw new InvalidSourceException("Слишком много файлов: " + files.size() + " (не более " + MAX_FILES + ")");
        }
        Set<String> seen = new HashSet<>();
        long total = 0;
        var bytes = new ByteArrayOutputStream();
        try (var tar = new TarArchiveOutputStream(bytes, StandardCharsets.UTF_8.name())) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (SourceFile file : files) {
                String path = validatePath(file.path());
                if (!seen.add(path)) {
                    throw new InvalidSourceException("Файл указан дважды: " + path);
                }
                byte[] content = file.content().getBytes(StandardCharsets.UTF_8);
                total += content.length;
                if (total > MAX_TOTAL_BYTES) {
                    throw new InvalidSourceException("Суммарный размер файлов превышает 256 КБ");
                }
                var entry = new TarArchiveEntry(path);
                entry.setSize(content.length);
                entry.setMode(0644);
                tar.putArchiveEntry(entry);
                tar.write(content);
                tar.closeArchiveEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    static String validatePath(String path) {
        if (path == null || !ALLOWED_PATH.matcher(path).matches()) {
            throw new InvalidSourceException("Недопустимый путь файла: " + path);
        }
        return path;
    }
}
