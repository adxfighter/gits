package ru.gits.api.session;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.gits.core.task.FileKind;
import ru.gits.core.task.TaskFile;
import ru.gits.core.task.TaskVariant;

/** Code snapshots of a session task: JSON {path: content} of the editable files only. */
final class TaskCode {

    private static final ObjectMapper JSON = new ObjectMapper();

    private TaskCode() {
    }

    /** Editable starter files as the candidate first sees them. */
    static Map<String, String> starter(List<TaskFile> files) {
        Map<String, String> code = new TreeMap<>();
        files.stream().filter(TaskCode::isEditable).forEach(file -> code.put(file.getPath(), file.getContent()));
        return code;
    }

    static boolean isEditable(TaskFile file) {
        return file.getKind() == FileKind.STARTER && file.isEditable();
    }

    /** The saved snapshot, or the starter when nothing was saved yet. */
    static Map<String, String> current(String snapshot, List<TaskFile> files) {
        if (snapshot == null || snapshot.isBlank()) {
            return starter(files);
        }
        try {
            return new TreeMap<>(JSON.readValue(snapshot, new TypeReference<LinkedHashMap<String, String>>() { }));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored code snapshot is not valid JSON", e);
        }
    }

    static String json(Map<String, String> code) {
        try {
            return JSON.writeValueAsString(new TreeMap<>(code));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise a code snapshot", e);
        }
    }

    static int sizeBytes(Map<String, String> code) {
        return code.entrySet().stream()
                .mapToInt(e -> e.getKey().getBytes(StandardCharsets.UTF_8).length
                        + e.getValue().getBytes(StandardCharsets.UTF_8).length)
                .sum();
    }

    /** First "# heading" of the statement, which every variant starts with. */
    static String title(TaskVariant variant) {
        return variant.getStatementMd().lines()
                .filter(line -> line.startsWith("# "))
                .map(line -> line.substring(2).strip())
                .findFirst()
                .orElse(variant.getTemplate().getTitle());
    }
}
