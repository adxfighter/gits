package ru.gits.taskbank;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;

import ru.gits.taskbank.check.SchemaCheck;

/** Russian titles of the competency codes from tasks/competencies.yaml (code → title). */
public final class CompetencyTitles {

    private CompetencyTitles() {
    }

    /** Titles from {@code competencies.yaml} in the given tasks/ directory; empty when there is no such file. */
    public static Map<String, String> read(Path tasksDir) {
        Path file = tasksDir.resolve("competencies.yaml");
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        Map<String, String> titles = new TreeMap<>();
        JsonNode competencies = SchemaCheck.readYaml(file).path("competencies");
        competencies.fieldNames().forEachRemaining(code -> titles.put(code, competencies.path(code).path("title").asText()));
        return titles;
    }
}
