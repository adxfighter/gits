package ru.gits.api.session;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import ru.gits.core.task.FileKind;
import ru.gits.core.task.TaskFile;

/**
 * The retyping files of a warm-up variant (tasks/java/CAL-calibration): the read-only sample {@code Sample.txt} and
 * the editable {@code Typing.txt}. Both are text files: shown to the candidate, never compiled.
 */
public record CalibrationFiles(TaskFile sample, TaskFile typing) {

    public static final String SAMPLE = "Sample.txt";
    public static final String TYPING = "Typing.txt";

    /** The retyping files among the candidate-visible files of a variant, if it has them. */
    public static Optional<CalibrationFiles> of(List<TaskFile> files) {
        TaskFile sample = null;
        TaskFile typing = null;
        for (TaskFile file : files) {
            String name = file.getPath().substring(file.getPath().lastIndexOf('/') + 1);
            if (name.equals(SAMPLE) && file.getKind() == FileKind.READONLY) {
                sample = file;
            } else if (name.equals(TYPING) && file.getKind() == FileKind.STARTER && file.isEditable()) {
                typing = file;
            }
        }
        return sample == null || typing == null ? Optional.empty() : Optional.of(new CalibrationFiles(sample, typing));
    }

    /**
     * The largest block of text that appeared in the typing file at once, in non-whitespace characters, from stored
     * telemetry events (docs/telemetry.md). In the warm-up nothing inserts several characters by itself (completion,
     * auto-closing and auto-indent are off), so any single insertion — pasted, dropped or injected, whatever its
     * {@code source} — counts; undo and redo only bring back what was typed. A paste event's length is used only for
     * a paste whose edit was not recorded.
     */
    public int largestPaste(Iterable<JsonNode> events) {
        int fromEdits = 0;
        boolean pasteEdit = false;
        int fromPasteEvents = 0;
        for (JsonNode event : events) {
            if (!typing.getPath().equals(event.path("file").asText())) {
                continue;
            }
            if ("edit".equals(event.path("type").asText())) {
                pasteEdit |= "paste".equals(event.path("source").asText());
                boolean restoring = event.path("isUndo").asBoolean() || event.path("isRedo").asBoolean();
                if (!restoring) {
                    fromEdits = Math.max(fromEdits, RetypingCheck.meaningfulLength(event.path("text").asText("")));
                }
            } else if ("paste".equals(event.path("type").asText())) {
                fromPasteEvents = Math.max(fromPasteEvents, event.path("length").asInt());
            }
        }
        return pasteEdit || fromEdits > 0 ? fromEdits : fromPasteEvents;
    }
}
