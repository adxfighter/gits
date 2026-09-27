package ru.gits.task.logistics.cal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsing of durations written by dispatchers.
 */
public final class Durations {

    private static final Pattern DURATION = Pattern.compile("(?:(\\d{1,4})h)?\\s*(?:(\\d{1,4})m)?");

    private Durations() {
    }

    /**
     * Parses a duration like "1h 30m", "45m" or "2h" into minutes.
     *
     * @throws IllegalArgumentException when the text is not such a duration
     */
    public static int toMinutes(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Duration is empty");
        }
        Matcher matcher = DURATION.matcher(text.strip());
        if (!matcher.matches() || (matcher.group(1) == null && matcher.group(2) == null)) {
            throw new IllegalArgumentException("Not a duration: " + text);
        }
        int hours = matcher.group(1) == null ? 0 : Integer.parseInt(matcher.group(1));
        int minutes = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        return hours * 60 + minutes;
    }
}
