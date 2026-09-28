package ru.gits.api.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import ru.gits.core.result.TrustLevel;

/** Every indicator on synthetic event streams (P12): honest typing, a large paste, pause then burst, transcription. */
class IndicatorCalculatorTest {

    private static final String FILE = "src/main/java/Solution.java";

    private final IndicatorProperties properties = new IndicatorProperties(Duration.ofSeconds(5),
            Duration.ofSeconds(30), 150, Duration.ofSeconds(20), 2, List.of());
    private final IndicatorCalculator calculator = new IndicatorCalculator(properties);

    // --- stream builder ------------------------------------------------------------------------------------------

    private static final class Stream {
        final List<InputEvent> events = new ArrayList<>();
        double t;
        int cursor;

        /** Types {@code text} one character after another at {@code charsPerSecond}, at the cursor. */
        Stream type(String text, double charsPerSecond) {
            for (char c : text.toCharArray()) {
                t += 1000 / charsPerSecond;
                events.add(new InputEvent(t, "kd", null, null, 0, 0, 0, false, null, "letter", 0));
                events.add(edit(t, cursor, 0, String.valueOf(c), "typing"));
                cursor++;
            }
            return this;
        }

        Stream backspace(int count) {
            for (int i = 0; i < count; i++) {
                t += 150;
                cursor--;
                events.add(edit(t, cursor, 1, "", "typing"));
            }
            return this;
        }

        Stream moveTo(int offset) {
            cursor = offset;
            return this;
        }

        Stream paste(String text) {
            t += 200;
            events.add(new InputEvent(t, "paste", null, FILE, 0, 0, 0, false, null, null, text.length()));
            events.add(edit(t, cursor, 0, text, "paste"));
            cursor += text.length();
            return this;
        }

        /** Ctrl+Z of the last paste. */
        Stream undoPaste(int length) {
            t += 300;
            cursor -= length;
            events.add(new InputEvent(t, "edit", "other", FILE, cursor, length, 0, true, null, null, 0));
            return this;
        }

        /** A paste of text that was already in the task's code or statement (ownCode). */
        Stream pasteOwn(String text) {
            t += 200;
            events.add(new InputEvent(t, "edit", "paste", FILE, cursor, 0, text.length(), false, null, null, 0, true));
            cursor += text.length();
            return this;
        }

        Stream copy(int length) {
            t += 100;
            events.add(new InputEvent(t, "copy", null, FILE, 0, 0, 0, false, null, null, length));
            return this;
        }

        /** Enter with auto-indent: one key, the editor inserts a line break and the indentation (and a brace). */
        Stream enterWithIndent() {
            t += 250;
            String inserted = "\n        \n    }";
            events.add(new InputEvent(t, "kd", null, null, 0, 0, 0, false, null, "enter", 0));
            events.add(edit(t, cursor, 0, inserted, "typing"));
            cursor += inserted.length();
            return this;
        }

        Stream pause(double seconds) {
            t += seconds * 1000;
            return this;
        }

        Stream event(String type, String state) {
            t += 1;
            events.add(new InputEvent(t, type, null, null, 0, 0, 0, false, state, null, 0));
            return this;
        }

        private static InputEvent edit(double t, int offset, int rangeLength, String text, String source) {
            return new InputEvent(t, "edit", source, FILE, offset, rangeLength, text.length(), false, null, null, 0);
        }
    }

    private static String chars(int count) {
        return "abcdefghij".repeat(count / 10 + 1).substring(0, count);
    }

    // --- scenarios -----------------------------------------------------------------------------------------------

    @Test
    void honestTypingWithCorrectionsLooksNatural() {
        Stream s = new Stream()
                .type(chars(120), 5).backspace(10).type(chars(40), 4)
                .moveTo(30).type(chars(60), 5).backspace(5)
                .moveTo(10).type(chars(40), 3);

        var result = calculator.compute(s.events, 245, 5.0, 300.0, 6);

        assertThat(result.pasteRatio()).isZero();
        assertThat(result.largestPaste()).isZero();
        assertThat(result.idleThenBurst()).isZero();
        assertThat(result.burstMax()).isCloseTo(5, within(0.3));
        assertThat(result.burstRelative()).isCloseTo(1.0, within(0.1));
        assertThat(result.linearity()).isLessThan(0.9);
        assertThat(result.editRatio()).isCloseTo(15 / 260.0, within(0.001));
        assertThat(result.typedChars()).isEqualTo(260);
        assertThat(result.timeToFirstRunSeconds()).isEqualTo(300.0);
        assertThat(result.runsCount()).isEqualTo(6);
    }

    @Test
    void largePasteDominatesTheCode() {
        Stream s = new Stream().type(chars(100), 5).paste(chars(500));

        var result = calculator.compute(s.events, 600, null, null, 0);

        assertThat(result.pasteRatio()).isCloseTo(0.833, within(0.001));
        assertThat(result.pastedChars()).isEqualTo(500);
        assertThat(result.largestPaste()).isEqualTo(500);
        // pastes do not count as typing speed
        assertThat(result.burstMax()).isCloseTo(5, within(0.3));
        assertThat(result.burstRelative()).isNull();
        assertThat(result.timeToFirstRunSeconds()).isNull();
    }

    @Test
    void pauseThenBurstIsCountedOnlyWithoutPaste() {
        Stream burst = new Stream().type(chars(50), 4).pause(45).type(chars(200), 15);
        assertThat(calculator.compute(burst.events, 250, null, null, 0).idleThenBurst()).isEqualTo(1);

        // the same burst with a paste in it is a paste, not an "idle then burst"
        Stream pasted = new Stream().type(chars(50), 4).pause(45).type(chars(100), 15).paste(chars(100));
        assertThat(calculator.compute(pasted.events, 250, null, null, 0).idleThenBurst()).isZero();

        // a pause followed by ordinary typing is not a burst
        Stream calm = new Stream().type(chars(50), 4).pause(45).type(chars(100), 4);
        assertThat(calculator.compute(calm.events, 150, null, null, 0).idleThenBurst()).isZero();
    }

    @Test
    void linearTranscriptionIsTopToBottom() {
        Stream s = new Stream().type(chars(400), 6);

        var result = calculator.compute(s.events, 400, 6.0, null, 0);

        assertThat(result.linearity()).isEqualTo(1.0);
        assertThat(result.editRatio()).isZero();
    }

    @Test
    void focusLossEpisodesAndTheirLength() {
        Stream s = new Stream().type(chars(10), 5)
                .event("blur", null).pause(15).event("focus", null)
                .type(chars(10), 5)
                .event("visibility", "hidden").event("blur", null).pause(60).event("visibility", "visible")
                .event("focus", null)
                .type(chars(5), 5)
                .event("blur", null).pause(3).type(chars(1), 5);

        var result = calculator.compute(s.events, 26, null, null, 0);

        assertThat(result.focusLossCount()).isEqualTo(3);
        // 15 s + 60 s (hidden and blurred together count once) + ~3 s until typing resumed
        assertThat(result.focusLossSeconds()).isCloseTo(78.2, within(0.5));
    }

    @Test
    void burstIsMeasuredOverTheSlidingWindowAgainstTheBaseline() {
        // 30 characters in one second, then slow typing
        Stream s = new Stream().type(chars(30), 30).pause(10).type(chars(10), 2);

        var result = calculator.compute(s.events, 40, 3.0, null, 0);

        assertThat(result.burstMax()).isCloseTo(6.0, within(0.01));
        assertThat(result.burstRelative()).isCloseTo(2.0, within(0.01));
    }

    @Test
    void whatTheEditorInsertsByItselfDoesNotRaiseTheTypingSpeed() {
        Stream s = new Stream();
        for (int i = 0; i < 10; i++) {
            s.type(chars(2), 4).enterWithIndent();
        }

        var result = calculator.compute(s.events, 200, 4.0, null, 0);

        // 30 key presses over 7.5 s: 4 per second, however many characters auto-indent added
        assertThat(result.burstMax()).isCloseTo(4.0, within(0.3));
        assertThat(result.burstRelative()).isCloseTo(1.0, within(0.1));
    }

    @Test
    void anUndonePasteAndMovingOwnCodeAreNotPastes() {
        Stream undone = new Stream().type(chars(100), 5).paste(chars(400)).undoPaste(400);
        var first = calculator.compute(undone.events, 100, null, null, 0);
        assertThat(first.pastedChars()).isZero();
        assertThat(first.pasteRatio()).isZero();

        Stream moved = new Stream().type(chars(300), 5).copy(120).paste(chars(120));
        var second = calculator.compute(moved.events, 420, null, null, 0);
        assertThat(second.largestPaste()).isZero();

        Stream foreign = new Stream().type(chars(300), 5).copy(120).paste(chars(250));
        assertThat(calculator.compute(foreign.events, 550, null, null, 0).largestPaste()).isEqualTo(250);
    }

    @Test
    void pastesOfTheTasksOwnCodeOrStatementAreNotCopying() {
        Stream s = new Stream().type(chars(200), 5).pasteOwn(chars(150)).paste(chars(40));

        var result = calculator.compute(s.events, 390, null, null, 0);

        assertThat(result.pastedChars()).isEqualTo(40);
        assertThat(result.largestPaste()).isEqualTo(40);
        assertThat(result.externalPastes()).isEqualTo(1);
        assertThat(calculator.compute(new Stream().type(chars(200), 5).pasteOwn(chars(150)).events, 350, null, null,
                0).externalPastes()).isZero();
    }

    @Test
    void externalPastesCountNonWhitespaceCharactersLikeThePage() {
        // "\n        i++;" from outside: 13 characters, but 4 without whitespace — below the threshold
        List<InputEvent> events = List.of(
                new InputEvent(1000, "edit", "paste", FILE, 0, 0, 13, false, null, null, 0, false, 4),
                new InputEvent(2000, "edit", "paste", FILE, 13, 0, 30, false, null, null, 0, false, 24));

        assertThat(calculator.compute(events, 43, null, null, 0).externalPastes()).isEqualTo(1);
    }

    @Test
    void activityAfterAReloadEndsTheAwayEpisode() {
        // the old page reported "hidden" when it was reloaded; the new one only moves the cursor
        Stream s = new Stream().type(chars(10), 5).event("visibility", "hidden").pause(2)
                .event("cursor", null).pause(120).event("cursor", null);

        var result = calculator.compute(s.events, 10, null, null, 0);

        assertThat(result.focusLossCount()).isEqualTo(1);
        assertThat(result.focusLossSeconds()).isLessThan(3);
    }

    @Test
    void theOrderOfArrivalDoesNotMatter() {
        Stream s = new Stream().type(chars(50), 4).pause(45).type(chars(200), 15).paste(chars(60));
        List<InputEvent> shuffled = new ArrayList<>(s.events);
        java.util.Collections.shuffle(shuffled, new java.util.Random(7));

        assertThat(calculator.compute(shuffled, 310, 4.0, null, 0)).isEqualTo(calculator.compute(s.events, 310, 4.0,
                null, 0));
    }

    @Test
    void linearityIsFollowedPerFile() {
        List<InputEvent> events = new ArrayList<>();
        double t = 0;
        for (int i = 0; i < 50; i++) {
            t += 200;
            events.add(new InputEvent(t, "edit", "typing", i % 2 == 0 ? "A.java" : "B.java", i / 2, 0, 1, false, null,
                    null, 0));
        }
        // typing alternately at the end of two files is still top to bottom in each
        assertThat(calculator.compute(events, 50, null, null, 0).linearity()).isEqualTo(1.0);
    }

    @Test
    void emptyTelemetryGivesNeutralValues() {
        var result = calculator.compute(List.of(), 120, null, null, 0);

        assertThat(result.telemetryEvents()).isZero();
        assertThat(result.pasteRatio()).isZero();
        assertThat(result.linearity()).isZero();
        assertThat(result.focusLossCount()).isZero();
    }

    @Test
    void defaultRulesJudgeTheScenarios() {
        TrustRules rules = new TrustRules(List.of(
                new IndicatorProperties.Rule(TrustLevel.RED, List.of("pasteRatio > 0.5"), "paste"),
                new IndicatorProperties.Rule(TrustLevel.RED, List.of("idleThenBurst >= 2", "focusLossSeconds > 60"),
                        "idle"),
                new IndicatorProperties.Rule(TrustLevel.YELLOW, List.of("linearity > 0.9", "typedChars > 300"),
                        "linear")));

        var honest = calculator.compute(new Stream().type(chars(120), 5).moveTo(10).type(chars(40), 4).events,
                160, null, null, 0);
        assertThat(rules.evaluate(honest.values()).level()).isEqualTo(TrustLevel.GREEN);

        var pasted = calculator.compute(new Stream().type(chars(100), 5).paste(chars(500)).events, 600, null, null, 0);
        assertThat(rules.evaluate(pasted.values()).level()).isEqualTo(TrustLevel.RED);

        var transcribed = calculator.compute(new Stream().type(chars(400), 6).events, 400, null, null, 0);
        assertThat(rules.evaluate(transcribed.values()).level()).isEqualTo(TrustLevel.YELLOW);
        assertThat(rules.evaluate(transcribed.values()).reasons()).containsExactly("linear");

        Stream away = new Stream().type(chars(20), 4);
        for (int i = 0; i < 2; i++) {
            away.event("visibility", "hidden").pause(40).event("visibility", "visible").type(chars(200), 15);
        }
        var idle = calculator.compute(away.events, 420, null, null, 0);
        assertThat(idle.idleThenBurst()).isEqualTo(2);
        assertThat(rules.evaluate(idle.values()).level()).isEqualTo(TrustLevel.RED);
    }
}
