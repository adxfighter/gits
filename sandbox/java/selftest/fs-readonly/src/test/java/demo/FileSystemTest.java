package demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** Passes only when the image is read-only and just the tmpfs directories are writable. */
class FileSystemTest {

    @ParameterizedTest
    @ValueSource(strings = {"/pwned", "/opt/gits/pwned", "/opt/libs/pwned.jar", "/etc/pwned", "/usr/pwned"})
    void imageIsReadOnly(String path) {
        assertThatThrownBy(() -> Files.writeString(Path.of(path), "x")).isInstanceOf(IOException.class);
    }

    @Test
    void entrypointCannotBeReplaced() {
        assertThatThrownBy(() -> Files.writeString(Path.of("/opt/gits/entrypoint.sh"), "echo pwned"))
                .isInstanceOf(IOException.class);
    }

    @Test
    void tmpIsWritable() throws IOException {
        Path file = Files.createTempFile("gits", ".txt");
        Files.writeString(file, "ok");
        assertThat(Files.readString(file)).isEqualTo("ok");
    }
}
