package ru.gits.api.employer;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import ru.gits.api.config.GitsProperties;
import ru.gits.taskbank.CompetencyTitles;

/**
 * Russian titles of competencies for the employer report, read once from tasks/competencies.yaml of the mounted task
 * bank. Without the bank, or for a code missing there, the code itself is shown.
 */
@Component
class CompetencyCatalog {

    private final Map<String, String> titles;

    CompetencyCatalog(GitsProperties properties) {
        GitsProperties.Taskbank taskbank = properties.taskbank();
        this.titles = taskbank == null || taskbank.path() == null || taskbank.path().isBlank()
                ? Map.of()
                : CompetencyTitles.read(Path.of(taskbank.path()));
    }

    List<String> titles(List<String> codes) {
        return codes.stream().map(code -> titles.getOrDefault(code, code)).toList();
    }
}
