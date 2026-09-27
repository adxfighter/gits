package ru.gits.taskbank;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Typed views of template.yaml and task.yaml (validated against tasks/schema before binding). */
public final class TaskSpecs {

    private TaskSpecs() {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record TemplateSpec(
            String code,
            String title,
            List<String> competencies,
            @JsonProperty("base_level") String baseLevel,
            String description,
            @JsonProperty("difficulty_model") Map<String, Object> difficultyModel,
            List<String> domains) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record VariantSpec(
            String code,
            String template,
            String kind,
            String level,
            String domain,
            @JsonProperty("time_limit_min") int timeLimitMin,
            @JsonProperty("difficulty_params") Map<String, Object> difficultyParams,
            List<String> editable,
            List<String> readonly,
            @JsonProperty("flaky_policy") FlakyPolicy flakyPolicy,
            List<String> rubric) {

        public boolean isCalibration() {
            return "calibration".equals(kind);
        }
    }

    public record FlakyPolicy(
            int runs,
            @JsonProperty("reference_pass_min") int referencePassMin,
            @JsonProperty("starter_fail_min") int starterFailMin) {
    }
}
