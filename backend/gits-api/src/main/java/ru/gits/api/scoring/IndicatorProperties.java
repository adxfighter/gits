package ru.gits.api.scoring;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import ru.gits.core.result.TrustLevel;

/** Thresholds and trust rules of the indicators (gits.indicators), see docs/indicators.md. */
@ConfigurationProperties(prefix = "gits.indicators")
public record IndicatorProperties(Duration burstWindow, Duration idlePause, int idleBurstChars,
                                  Duration idleBurstWindow, int linearTolerance, List<Rule> rules) {

    /** All conditions ("name op number", op one of > >= < <= ==) must hold; then the task gets {@code level}. */
    public record Rule(TrustLevel level, List<String> when, String explanation) {
    }
}
