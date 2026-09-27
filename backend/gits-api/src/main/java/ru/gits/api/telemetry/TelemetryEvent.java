package ru.gits.api.telemetry;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One input event of the candidate, see docs/telemetry.md. Every field except {@code t} and {@code type} belongs to
 * particular types only; {@link #normalized()} keeps just those, so nothing else the client sends is ever stored
 * (e.g. the key character of a key event).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TelemetryEvent(
        Double t,
        String type,
        String keyClass,
        Boolean repeat,
        String file,
        Integer rangeOffset,
        Integer rangeLength,
        Integer textLength,
        String text,
        @JsonProperty("isUndo") Boolean isUndo,
        @JsonProperty("isRedo") Boolean isRedo,
        String source,
        Integer length,
        Boolean accepted,
        Integer insertedLength,
        String state,
        Integer offset,
        Integer width,
        Integer height) {

    public static final Set<String> TYPES = Set.of("kd", "ku", "edit", "cursor", "select", "paste", "copy", "focus",
            "blur", "visibility", "completion", "run", "submit", "resize");
    public static final Set<String> KEY_CLASSES = Set.of("letter", "digit", "space", "enter", "backspace", "delete",
            "tab", "arrow", "punct", "bracket", "modifier", "other");
    public static final Set<String> EDIT_SOURCES = Set.of("typing", "paste", "completion", "other");
    public static final Set<String> VISIBILITY_STATES = Set.of("visible", "hidden");

    /**
     * Checks the event against its type. Returns an error message, or null when the event is valid.
     */
    public String validate() {
        if (t == null || !Double.isFinite(t) || t < 0) {
            return "t — неотрицательное число миллисекунд";
        }
        if (type == null || !TYPES.contains(type)) {
            return "неизвестный type: " + type;
        }
        return switch (type) {
            case "kd", "ku" -> {
                if (keyClass == null || !KEY_CLASSES.contains(keyClass)) {
                    yield "неизвестный keyClass: " + keyClass;
                }
                yield "kd".equals(type) && repeat == null ? "для kd обязателен repeat" : null;
            }
            case "edit" -> {
                if (file == null || rangeOffset == null || rangeLength == null || textLength == null
                        || text == null || source == null) {
                    yield "для edit обязательны file, rangeOffset, rangeLength, textLength, text, source";
                }
                if (rangeOffset < 0 || rangeLength < 0) {
                    yield "rangeOffset и rangeLength не могут быть отрицательными";
                }
                if (textLength != text.length()) {
                    yield "textLength не совпадает с длиной text";
                }
                yield EDIT_SOURCES.contains(source) ? null : "неизвестный source: " + source;
            }
            case "cursor" -> file == null || offset == null || offset < 0
                    ? "для cursor обязательны file и offset" : null;
            case "select" -> file == null || offset == null || length == null || offset < 0 || length < 0
                    ? "для select обязательны file, offset и length" : null;
            case "paste", "copy" -> file == null || length == null || length < 0
                    ? "для " + type + " обязательны file и length" : null;
            case "completion" -> accepted == null || insertedLength == null || insertedLength < 0
                    ? "для completion обязательны accepted и insertedLength" : null;
            case "visibility" -> state == null || !VISIBILITY_STATES.contains(state)
                    ? "для visibility state — visible или hidden" : null;
            case "resize" -> width == null || height == null || width <= 0 || height <= 0
                    ? "для resize обязательны положительные width и height" : null;
            default -> null; // focus, blur, run, submit carry only t
        };
    }

    /** The event with only the fields of its type; call after {@link #validate()}. */
    public TelemetryEvent normalized() {
        return switch (type) {
            case "kd", "ku" -> new TelemetryEvent(t, type, keyClass, Boolean.TRUE.equals(repeat), null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null, null);
            case "edit" -> new TelemetryEvent(t, type, null, null, file, rangeOffset, rangeLength, textLength, text,
                    Boolean.TRUE.equals(isUndo), Boolean.TRUE.equals(isRedo), source, null, null, null, null, null,
                    null, null);
            case "cursor" -> new TelemetryEvent(t, type, null, null, file, null, null, null, null, null, null, null,
                    null, null, null, null, offset, null, null);
            case "select" -> new TelemetryEvent(t, type, null, null, file, null, null, null, null, null, null, null,
                    length, null, null, null, offset, null, null);
            case "paste", "copy" -> new TelemetryEvent(t, type, null, null, file, null, null, null, null, null, null,
                    null, length, null, null, null, null, null, null);
            case "completion" -> new TelemetryEvent(t, type, null, null, null, null, null, null, null, null, null,
                    null, null, accepted, insertedLength, null, null, null, null);
            case "visibility" -> new TelemetryEvent(t, type, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, state, null, null, null);
            case "resize" -> new TelemetryEvent(t, type, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, width, height);
            default -> new TelemetryEvent(t, type, null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null);
        };
    }
}
