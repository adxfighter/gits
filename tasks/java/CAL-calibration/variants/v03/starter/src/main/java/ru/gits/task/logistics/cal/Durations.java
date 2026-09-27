package ru.gits.task.logistics.cal;

/**
 * Parsing of durations written by dispatchers.
 */
public final class Durations {

    private Durations() {
    }

    /**
     * Parses a duration like "1h 30m", "45m" or "2h" into minutes.
     *
     * @throws IllegalArgumentException when the text is not such a duration
     */
    public static int toMinutes(String text) {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
