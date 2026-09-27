package ru.gits.taskbank;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Reads and writes validation.json (pretty-printed, LF line endings, UTF-8). */
public final class ReportFiles {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private ReportFiles() {
    }

    public static void write(Path variantDir, ValidationReport report) {
        try {
            String json = MAPPER.writeValueAsString(report).replace("\r\n", "\n") + "\n";
            Files.writeString(variantDir.resolve(VariantSources.VALIDATION_FILE), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Optional<ValidationReport> read(Path variantDir) {
        Path file = variantDir.resolve(VariantSources.VALIDATION_FILE);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(file.toFile(), ValidationReport.class));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    /** The stored report is trustworthy only when it passed and its hash matches the current files. */
    public static Optional<String> problemWithStoredReport(Path variantDir) {
        Optional<ValidationReport> stored = read(variantDir);
        if (stored.isEmpty()) {
            return Optional.of("нет validation.json — запустите validate");
        }
        if (stored.get().status() != ValidationReport.Status.PASSED) {
            return Optional.of("последняя валидация не пройдена");
        }
        String actual = ContentHash.of(VariantSources.read(variantDir).allFiles());
        if (!actual.equals(stored.get().contentHash())) {
            return Optional.of("файлы изменены после валидации (content_hash не совпадает)");
        }
        return Optional.empty();
    }
}
