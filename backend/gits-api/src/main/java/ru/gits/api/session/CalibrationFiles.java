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
     * The largest paste into the typing file, in non-whitespace characters, from stored telemetry events
     * (docs/telemetry.md): an edit with {@code source: paste}, or a paste event where no edit was recorded.
     */
    public int largestPaste(Iterable<JsonNode> events) {
        int fromEdits = 0;
        int fromPasteEvents = 0;
        for (JsonNode event : events) {
            if (!typing.getPath().equals(event.path("file").asText())) {
                continue;
            }
            if ("edit".equals(event.path("type").asText()) && "paste".equals(event.path("source").asText())) {
                fromEdits = Math.max(fromEdits, RetypingCheck.meaningfulLength(event.path("text").asText("")));
            } else if ("paste".equals(event.path("type").asText())) {
                fromPasteEvents = Math.max(fromPasteEvents, event.path("length").asInt());
            }
        }
        // the pasted text of an edit counts only non-whitespace; a paste event's length is the fallback
        return fromEdits > 0 ? fromEdits : fromPasteEvents;
    }
}
