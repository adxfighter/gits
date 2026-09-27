package ru.gits.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SourceArchiveTest {

    @Test
    void writesFilesIntoTar() throws Exception {
        byte[] tar = SourceArchive.build(List.of(
                new SourceFile("src/main/java/demo/Calc.java", "package demo; class Calc {} // кириллица"),
                new SourceFile("src/test/java/demo/CalcTest.java", "package demo; class CalcTest {}")));

        List<String> names = new ArrayList<>();
        String firstContent = null;
        try (var in = new TarArchiveInputStream(new ByteArrayInputStream(tar))) {
            for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                names.add(entry.getName());
                if (firstContent == null) {
                    firstContent = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        assertThat(names).containsExactly("src/main/java/demo/Calc.java", "src/test/java/demo/CalcTest.java");
        assertThat(firstContent).endsWith("кириллица");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../etc/passwd", "/etc/passwd", "src/main/java/../../x.java", "src/main/java/demo/Calc.txt",
            "src/main/resources/app.java", "src\\main\\java\\A.java", "src/main/java/demo/.hidden.java",
            "src/main/java/demo//A.java", "src/main/java/1demo/A.java", ""})
    void rejectsUnsafePaths(String path) {
        assertThatThrownBy(() -> SourceArchive.build(List.of(new SourceFile(path, "class A {}"))))
                .isInstanceOf(InvalidSourceException.class)
                .hasMessageContaining("Недопустимый путь");
    }

    @Test
    void rejectsTooManyFiles() {
        List<SourceFile> files = IntStream.range(0, SourceArchive.MAX_FILES + 1)
                .mapToObj(i -> new SourceFile("src/main/java/demo/A" + i + ".java", "class A" + i + " {}"))
                .toList();

        assertThatThrownBy(() -> SourceArchive.build(files)).hasMessageContaining("Слишком много файлов");
    }

    @Test
    void rejectsOversizedContent() {
        String big = "x".repeat(SourceArchive.MAX_TOTAL_BYTES + 1);

        assertThatThrownBy(() -> SourceArchive.build(List.of(new SourceFile("src/main/java/A.java", big))))
                .hasMessageContaining("256 КБ");
    }

    @Test
    void rejectsDuplicatesAndEmptyInput() {
        var file = new SourceFile("src/main/java/A.java", "class A {}");

        assertThatThrownBy(() -> SourceArchive.build(List.of(file, file))).hasMessageContaining("дважды");
        assertThatThrownBy(() -> SourceArchive.build(List.of())).hasMessageContaining("Нет файлов");
    }
}
