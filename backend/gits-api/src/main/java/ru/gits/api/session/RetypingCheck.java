package ru.gits.api.session;

import java.util.Locale;

/**
 * Check of the warm-up retyping (part 1): how closely the typed text matches the sample, ignoring all whitespace
 * (spaces, indentation, line breaks), and whether text was pasted instead of typed. Nothing is compiled or run.
 * The retyping passes at {@value #PASS_PERCENT}% or more; any paste of {@value #PASTE_MIN_CHARS} or more
 * non-whitespace characters into the typing file is a suspicion of copying.
 */
public final class RetypingCheck {

    public static final double PASS_PERCENT = 95.0;
    public static final int PASTE_MIN_CHARS = 10;
    /** Longer typed texts are not compared exactly: the edit distance is quadratic, and they fail anyway. */
    static final int MAX_LENGTH_RATIO = 3;

    /** {@code similarityPercent} 0-100 with one decimal; {@code message} is shown to the candidate as it is. */
    public record Result(double similarityPercent, boolean passed, boolean pasteSuspected, String message) {
    }

    private RetypingCheck() {
    }

    /**
     * @param largestPaste the largest paste into the typing file, in non-whitespace characters (0 if none)
     */
    public static Result evaluate(String sample, String typed, int largestPaste) {
        double similarity = similarityPercent(sample, typed);
        boolean passed = similarity >= PASS_PERCENT;
        boolean pasted = largestPaste >= PASTE_MIN_CHARS;
        String percent = format(similarity);
        String message;
        if (pasted) {
            message = "Подозрение на копирование: в файл вставлен текст из буфера обмена. Перепечатайте фрагмент "
                    + "вручную. Совпадение с образцом — " + percent + ".";
        } else if (passed) {
            message = "Перепечатка засчитана: текст совпадает с образцом на " + percent + ".";
        } else {
            message = "Напечатанный текст отличается от первоначального более 5 % (совпадение с образцом — "
                    + percent + ").";
        }
        return new Result(similarity, passed && !pasted, pasted, message);
    }

    /**
     * Similarity in percent: 100 × (1 − edit distance / length of the longer text), both texts without whitespace.
     * An empty typed text gives 0. A typed text more than {@value #MAX_LENGTH_RATIO} times longer than the sample
     * gets the upper bound 1 − length difference / longer length instead of the exact value (well below 95% either
     * way), so one check never costs more than a few milliseconds.
     */
    public static double similarityPercent(String sample, String typed) {
        String a = withoutWhitespace(sample);
        String b = withoutWhitespace(typed);
        int longer = Math.max(a.length(), b.length());
        if (longer == 0) {
            return 100.0;
        }
        int shorter = Math.min(a.length(), b.length());
        int distance = shorter > 0 && longer <= (long) MAX_LENGTH_RATIO * shorter ? distance(a, b) : longer - shorter;
        double similarity = 1.0 - (double) distance / longer;
        return Math.floor(similarity * 1000) / 10.0;
    }

    /** Non-whitespace characters of a text, e.g. of a paste. */
    public static int meaningfulLength(String text) {
        return withoutWhitespace(text == null ? "" : text).length();
    }

    static String withoutWhitespace(String text) {
        StringBuilder result = new StringBuilder(text.length());
        text.codePoints().filter(c -> !isSpace(c)).forEach(result::appendCodePoint);
        return result.toString();
    }

    /** Whitespace in the broad sense: also no-break and zero-width spaces a copied text may bring along. */
    private static boolean isSpace(int c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == 0x200B || c == 0xFEFF;
    }

    /** Levenshtein distance in two rows: a warm-up fragment has about a thousand characters. */
    static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j], current[j - 1]) + 1);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private static String format(double percent) {
        return String.format(Locale.ROOT, "%.1f", percent).replace('.', ',') + "%";
    }
}
