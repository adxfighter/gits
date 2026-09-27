package ru.gits.task.telecom.cal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TypingTest {

    /** The same fragment as in Reference.java. */
    private static final String EXPECTED = """
            public final class TariffCalculator {

                private final Map<String, Tariff> tariffs = new HashMap<>();

                public void register(Tariff tariff) {
                    Objects.requireNonNull(tariff, "tariff");
                    tariffs.put(tariff.code(), tariff);
                }

                public long monthlyFee(String code, int extraGigabytes) {
                    Tariff tariff = tariffs.get(code);
                    if (tariff == null) {
                        throw new IllegalArgumentException("Unknown tariff: " + code);
                    }
                    if (extraGigabytes < 0) {
                        throw new IllegalArgumentException("Negative traffic");
                    }
                    long fee = tariff.baseFee();
                    fee += (long) extraGigabytes * tariff.pricePerGigabyte();
                    return Math.min(fee, tariff.maxFee());
                }

                public List<String> codes() {
                    return tariffs.keySet().stream().sorted().toList();
                }
            }
            """;

    private static String withoutWhitespace(String text) {
        StringBuilder result = new StringBuilder();
        text.codePoints().filter(c -> !Character.isWhitespace(c)).forEach(result::appendCodePoint);
        return result.toString();
    }

    /** Line of EXPECTED that holds the non-whitespace character number {@code index}. */
    private static String lineOf(int index) {
        int seen = 0;
        String[] lines = EXPECTED.split("\n");
        for (int number = 0; number < lines.length; number++) {
            seen += withoutWhitespace(lines[number]).length();
            if (seen > index) {
                return "строка " + (number + 1) + ": " + lines[number].strip();
            }
        }
        return "конец фрагмента";
    }

    @Test
    void fragmentIsRetyped() {
        String expected = withoutWhitespace(EXPECTED);
        String typed = withoutWhitespace(Typing.TYPED);

        int mismatch = 0;
        while (mismatch < Math.min(expected.length(), typed.length())
                && expected.charAt(mismatch) == typed.charAt(mismatch)) {
            mismatch++;
        }
        assertThat(typed).as("первое расхождение — %s", lineOf(mismatch)).isEqualTo(expected);
    }
}
