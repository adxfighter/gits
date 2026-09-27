package ru.gits.taskbank;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** Content of validation.json. */
@JsonPropertyOrder({"code", "status", "content_hash", "validated_at", "validator_version", "runs", "checks"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidationReport(
        String code,
        Status status,
        @JsonProperty("content_hash") String contentHash,
        @JsonProperty("validated_at") Instant validatedAt,
        @JsonProperty("validator_version") int validatorVersion,
        Runs runs,
        List<Check> checks) {

    public static final int VALIDATOR_VERSION = 1;

    public enum Status {
        PASSED,
        FAILED
    }

    /** One check; {@code details} explains the result in Russian for task authors. */
    public record Check(String id, boolean passed, String details) {
    }

    /**
     * @param requested          runs configured by flaky_policy (or --runs)
     * @param referencePassed    runs where the reference solution passed every test
     * @param starterFailed      runs where the starter failed at least one hidden test
     * @param referenceMaxMs     slowest reference run, including container start
     */
    @JsonPropertyOrder({"requested", "reference_passed", "starter_failed", "reference_max_ms"})
    public record Runs(int requested,
                       @JsonProperty("reference_passed") int referencePassed,
                       @JsonProperty("starter_failed") int starterFailed,
                       @JsonProperty("reference_max_ms") long referenceMaxMs) {
    }
}
