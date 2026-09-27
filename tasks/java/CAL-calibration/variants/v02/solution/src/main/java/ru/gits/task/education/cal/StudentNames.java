package ru.gits.task.education.cal;

import java.util.Locale;

/**
 * Student name helpers.
 */
public final class StudentNames {

    private StudentNames() {
    }

    /**
     * Formats a full name typed in any case: words start with a capital letter, the rest is lower case,
     * words are separated by single spaces.
     *
     * @throws IllegalArgumentException when the name is empty
     */
    public static String format(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Name is empty");
        }
        StringBuilder result = new StringBuilder();
        for (String word : raw.strip().split("\\s+")) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            String lower = word.toLowerCase(Locale.ROOT);
            result.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
        }
        return result.toString();
    }
}
