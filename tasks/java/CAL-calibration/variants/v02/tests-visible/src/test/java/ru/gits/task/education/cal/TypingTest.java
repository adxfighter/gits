package ru.gits.task.education.cal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TypingTest {

    /** The same fragment as in Reference.java. */
    private static final String EXPECTED = """
            public final class GradeBook {

                private final Map<String, List<Integer>> grades = new TreeMap<>();

                public void add(String studentId, int grade) {
                    if (grade < 2 || grade > 5) {
                        throw new IllegalArgumentException("Grade must be from 2 to 5");
                    }
                    grades.computeIfAbsent(studentId, id -> new ArrayList<>()).add(grade);
                }

                public OptionalDouble average(String studentId) {
                    return grades.getOrDefault(studentId, List.of()).stream()
                            .mapToInt(Integer::intValue)
                            .average();
                }

                public List<String> excellentStudents() {
                    List<String> result = new ArrayList<>();
                    for (Map.Entry<String, List<Integer>> entry : grades.entrySet()) {
                        boolean allFives = entry.getValue().stream().allMatch(g -> g == 5);
                        if (allFives && !entry.getValue().isEmpty()) {
                            result.add(entry.getKey());
                        }
                    }
                    return result;
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
