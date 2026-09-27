package ru.gits.taskbank;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import ru.gits.sandbox.SourceFile;

/**
 * Files of one variant directory, read as UTF-8 with line endings normalised to LF.
 * Paths are sandbox paths ({@code src/main/java/...}) relative to the starter/solution/tests folders.
 */
public record VariantSources(
        Path directory,
        String statement,
        Map<String, String> starter,
        Map<String, String> solution,
        Map<String, String> visibleTests,
        Map<String, String> hiddenTests,
        Map<String, String> allFiles) {

    public static final String VALIDATION_FILE = "validation.json";

    public static VariantSources read(Path directory) {
        return new VariantSources(directory,
                readIfExists(directory.resolve("statement.md")),
                tree(directory.resolve("starter")),
                tree(directory.resolve("solution")),
                tree(directory.resolve("tests-visible")),
                tree(directory.resolve("tests-hidden")),
                allFiles(directory));
    }

    public List<SourceFile> starterFiles() {
        return toSources(starter);
    }

    public List<SourceFile> visibleTestFiles() {
        return toSources(visibleTests);
    }

    public List<SourceFile> hiddenTestFiles() {
        return toSources(hiddenTests);
    }

    /** Starter with the editable files replaced by the reference solution. */
    public List<SourceFile> solvedFiles() {
        Map<String, String> solved = new TreeMap<>(starter);
        solved.putAll(solution);
        return toSources(solved);
    }

    static String normalise(String text) {
        return text.replace("\r\n", "\n");
    }

    private static List<SourceFile> toSources(Map<String, String> files) {
        List<SourceFile> sources = new ArrayList<>();
        files.forEach((path, content) -> sources.add(new SourceFile(path, content)));
        return sources;
    }

    private static Map<String, String> tree(Path root) {
        Map<String, String> files = new TreeMap<>();
        if (!Files.isDirectory(root)) {
            return files;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).forEach(file ->
                    files.put(relative(root, file), normalise(readFile(file))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files;
    }

    private static Map<String, String> allFiles(Path directory) {
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                    .filter(file -> !file.equals(directory.resolve(VALIDATION_FILE)))
                    .forEach(file -> files.put(relative(directory, file), normalise(readFile(file))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files;
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static String readIfExists(Path file) {
        return Files.isRegularFile(file) ? normalise(readFile(file)) : null;
    }

    private static String readFile(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }
}
