package ru.gits.api.scoring;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import ru.gits.core.common.Level;

/**
 * Weights of the task levels and of the warm-up in the preliminary score (gits.scoring), see docs/indicators.md.
 */
@ConfigurationProperties(prefix = "gits.scoring")
public record ScoringProperties(Map<Level, BigDecimal> weights, BigDecimal calibrationWeight, Duration checkInterval,
                                Duration stuckSubmitAfter) {

    public ScoringProperties {
        calibrationWeight = calibrationWeight == null ? new BigDecimal("0.5") : calibrationWeight;
    }
}
