package ru.gits.task.logistics.cal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TypingTest {

    /** The same fragment as in Reference.java. */
    private static final String EXPECTED = """
            public final class RoutePlanner {

                private final List<Stop> stops = new ArrayList<>();

                public void addStop(String address, int unloadMinutes) {
                    if (address == null || address.isBlank()) {
                        throw new IllegalArgumentException("Address is required");
                    }
                    stops.add(new Stop(address.strip(), unloadMinutes));
                }

                public int totalMinutes(int drivingMinutesBetweenStops) {
                    int total = 0;
                    for (int i = 0; i < stops.size(); i++) {
                        total += stops.get(i).unloadMinutes();
                        if (i > 0) {
                            total += drivingMinutesBetweenStops;
                        }
                    }
                    return total;
                }

                public Optional<Stop> longestUnload() {
                    return stops.stream().max(Comparator.comparingInt(Stop::unloadMinutes));
                }

                public record Stop(String address, int unloadMinutes) {
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
