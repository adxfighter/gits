package ru.gits.task.telecom.cal;

/**
 * Phone number helpers.
 */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    /**
     * Normalizes a Russian mobile number to eleven digits starting with 7.
     *
     * @throws IllegalArgumentException when the text is not a Russian mobile number
     */
    public static String normalize(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("No number");
        }
        String digits = raw.replaceAll("[\\s()\\-]", "");
        if (digits.startsWith("+7")) {
            digits = digits.substring(1);
        } else if (digits.startsWith("8")) {
            digits = "7" + digits.substring(1);
        }
        if (!digits.matches("7\\d{10}")) {
            throw new IllegalArgumentException("Not a Russian mobile number: " + raw);
        }
        return digits;
    }
}
