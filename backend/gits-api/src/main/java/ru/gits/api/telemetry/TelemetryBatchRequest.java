package ru.gits.api.telemetry;

import java.util.List;

/**
 * A telemetry batch as the page sends it. {@code beaconToken} is filled only in a navigator.sendBeacon request
 * (text/plain), which cannot carry the CSRF header.
 */
public record TelemetryBatchRequest(Integer seq, Double clientTsStart, Double clientTsEnd,
                                    List<TelemetryEvent> events, String beaconToken) {
}
