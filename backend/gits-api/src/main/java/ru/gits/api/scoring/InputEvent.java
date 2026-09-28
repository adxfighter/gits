package ru.gits.api.scoring;

import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A stored telemetry event (docs/telemetry.md) with the fields the indicators use. {@code t} is milliseconds from
 * the start of the task.
 */
record InputEvent(double t, String type, String source, String file, int rangeOffset, int rangeLength,
                  int textLength, boolean undo, String state, String keyClass, int length, boolean ownCode,
                  int meaningfulLength) {

    InputEvent(double t, String type, String source, String file, int rangeOffset, int rangeLength, int textLength,
               boolean undo, String state, String keyClass, int length) {
        this(t, type, source, file, rangeOffset, rangeLength, textLength, undo, state, keyClass, length, false,
                textLength);
    }

    InputEvent(double t, String type, String source, String file, int rangeOffset, int rangeLength, int textLength,
               boolean undo, String state, String keyClass, int length, boolean ownCode) {
        this(t, type, source, file, rangeOffset, rangeLength, textLength, undo, state, keyClass, length, ownCode,
                textLength);
    }

    /** Keys that type a character (docs/telemetry.md key classes). */
    private static final Set<String> CHARACTER_KEYS = Set.of("letter", "digit", "space", "punct", "bracket", "enter",
            "tab");

    static InputEvent of(JsonNode node) {
        return new InputEvent(node.path("t").asDouble(), node.path("type").asText(), node.path("source").asText(null),
                node.path("file").asText(null), node.path("rangeOffset").asInt(), node.path("rangeLength").asInt(),
                node.path("textLength").asInt(), node.path("isUndo").asBoolean() || node.path("isRedo").asBoolean(),
                node.path("state").asText(null), node.path("keyClass").asText(null), node.path("length").asInt(),
                node.path("ownCode").asBoolean(false), meaningful(node.path("text").asText("")));
    }

    /** Non-whitespace characters, as the page counts a paste (own-code.ts). */
    private static int meaningful(String text) {
        return (int) text.codePoints().filter(c -> !Character.isWhitespace(c) && !Character.isSpaceChar(c)).count();
    }

    /**
     * A key press that types one character. Typing speed counts these, not edited characters: the editor adds
     * indentation and closing brackets by itself, and the calibration block, the baseline, does not.
     */
    boolean isCharacterKey() {
        return "kd".equals(type) && keyClass != null && CHARACTER_KEYS.contains(keyClass);
    }

    /** Any sign that the candidate is working on the page. */
    boolean isActivity() {
        return isEdit() || switch (type) {
            case "kd", "ku", "cursor", "select", "run", "submit", "paste", "copy" -> true;
            default -> false;
        };
    }

    boolean isEdit() {
        return "edit".equals(type);
    }

    /** Typed by hand: not pasted, not inserted by a completion, not an undo. */
    boolean isTyped() {
        return isEdit() && "typing".equals(source) && !undo;
    }

    boolean isPaste() {
        return isEdit() && "paste".equals(source);
    }

    /** A paste of text found neither in the task's code nor in its statement at the moment of the paste. */
    boolean isExternalPaste() {
        return isPaste() && !ownCode;
    }
}
