package ru.gits.api.scoring;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A stored telemetry event (docs/telemetry.md) with the fields the indicators use. {@code t} is milliseconds from
 * the start of the task.
 */
record InputEvent(double t, String type, String source, String file, int rangeOffset, int rangeLength,
                  int textLength, boolean undo, String state) {

    static InputEvent of(JsonNode node) {
        return new InputEvent(node.path("t").asDouble(), node.path("type").asText(), node.path("source").asText(null),
                node.path("file").asText(null), node.path("rangeOffset").asInt(), node.path("rangeLength").asInt(),
                node.path("textLength").asInt(), node.path("isUndo").asBoolean() || node.path("isRedo").asBoolean(),
                node.path("state").asText(null));
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
}
