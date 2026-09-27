package ru.gits.task.logistics.cal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TypingTest {

    private static String withoutWhitespace(String text) {
        return text.replaceAll("\\s+", "");
    }

    @Test
    void fragmentIsRetyped() {
        String expected = withoutWhitespace(Reference.FRAGMENT);
        String typed = withoutWhitespace(Typing.TYPED);

        int mismatch = 0;
        while (mismatch < Math.min(expected.length(), typed.length())
                && expected.charAt(mismatch) == typed.charAt(mismatch)) {
            mismatch++;
        }
        assertThat(typed)
                .as("первое расхождение после «%s»", expected.substring(Math.max(0, mismatch - 30), mismatch))
                .isEqualTo(expected);
    }
}
