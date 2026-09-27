package ru.gits.api.telemetry;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Limits of one telemetry batch, see docs/telemetry.md. */
@ConfigurationProperties(prefix = "gits.telemetry")
public record TelemetryProperties(int maxEvents, int maxBatchBytes) {
}
