package ru.gits.api.candidate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Current version of the candidate consent text (resources/consent/v{N}.md). */
@Component
public class ConsentText {

    public static final int CURRENT_VERSION = 1;

    private final String markdown;

    ConsentText() {
        try {
            this.markdown = new ClassPathResource("consent/v" + CURRENT_VERSION + ".md")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Consent text is missing", e);
        }
    }

    public String markdown() {
        return markdown;
    }
}
