package ru.gits.taskbank;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ContentHashTest {

    @Test
    void independentOfOrderAndLineEndings() {
        var unix = new LinkedHashMap<String, String>();
        unix.put("a.txt", "line 1\nline 2\n");
        unix.put("b/c.java", "class C {}\n");
        var windows = new LinkedHashMap<String, String>();
        windows.put("b/c.java", "class C {}\r\n");
        windows.put("a.txt", "line 1\r\nline 2\r\n");

        assertThat(ContentHash.of(unix)).isEqualTo(ContentHash.of(windows)).hasSize(64);
    }

    @Test
    void changesWithContentOrPath() {
        String base = ContentHash.of(Map.of("a.txt", "x"));

        assertThat(ContentHash.of(Map.of("a.txt", "y"))).isNotEqualTo(base);
        assertThat(ContentHash.of(Map.of("b.txt", "x"))).isNotEqualTo(base);
        // path/content boundaries are unambiguous
        assertThat(ContentHash.of(Map.of("ab", "c"))).isNotEqualTo(ContentHash.of(Map.of("a", "bc")));
    }
}
