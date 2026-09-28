package ru.gits.api.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RetypingCheckTest {

    private static final String SAMPLE = """
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
                    return tariff.baseFee() + (long) extraGigabytes * tariff.pricePerGigabyte();
                }
            }
            """;

    @Test
    void anExactRetypingPasses() {
        var result = RetypingCheck.evaluate(SAMPLE, SAMPLE, 0);
        assertThat(result.similarityPercent()).isEqualTo(100.0);
        assertThat(result.passed()).isTrue();
        assertThat(result.message()).startsWith("Перепечатка засчитана").contains("100,0%");
    }

    @Test
    void spacesIndentationAndLineBreaksDoNotMatter() {
        String flat = SAMPLE.replaceAll("\\s+", " ").replace("{ ", "{\n");
        assertThat(RetypingCheck.similarityPercent(SAMPLE, flat)).isEqualTo(100.0);
    }

    @Test
    void aFewTyposStillPass() {
        String typos = SAMPLE.replace("tariffs.put", "tarifs.put").replace("Unknown", "Unknwon")
                .replace("extraGigabytes)", "extraGigabites)");
        var result = RetypingCheck.evaluate(SAMPLE, typos, 0);
        assertThat(result.similarityPercent()).isBetween(95.0, 99.9);
        assertThat(result.passed()).isTrue();
    }

    @Test
    void moreThanFivePercentDifferenceFails() {
        // the last method is missing
        String half = SAMPLE.substring(0, SAMPLE.indexOf("public long"));
        var result = RetypingCheck.evaluate(SAMPLE, half, 0);
        assertThat(result.similarityPercent()).isLessThan(95.0);
        assertThat(result.passed()).isFalse();
        assertThat(result.message()).startsWith("Напечатанный текст отличается от первоначального более 5 %");
    }

    @Test
    void anEmptyRetypingIsZero() {
        assertThat(RetypingCheck.similarityPercent(SAMPLE, "")).isZero();
        assertThat(RetypingCheck.evaluate(SAMPLE, "   \n", 0).passed()).isFalse();
    }

    @Test
    void aPasteOfTenOrMoreCharactersIsASuspicionOfCopying() {
        var copied = RetypingCheck.evaluate(SAMPLE, SAMPLE, 250);
        assertThat(copied.pasteSuspected()).isTrue();
        // an exact text that was pasted does not count as a retyping
        assertThat(copied.passed()).isFalse();
        assertThat(copied.message()).startsWith("Подозрение на копирование");

        var piece = RetypingCheck.evaluate(SAMPLE, SAMPLE, RetypingCheck.PASTE_MIN_CHARS);
        assertThat(piece.pasteSuspected()).isTrue();

        var word = RetypingCheck.evaluate(SAMPLE, SAMPLE, RetypingCheck.PASTE_MIN_CHARS - 1);
        assertThat(word.pasteSuspected()).isFalse();
        assertThat(word.passed()).isTrue();
    }

    @Test
    void meaningfulLengthIgnoresWhitespace() {
        assertThat(RetypingCheck.meaningfulLength("  a b\n\tc  ")).isEqualTo(3);
        assertThat(RetypingCheck.meaningfulLength(null)).isZero();
    }
}
