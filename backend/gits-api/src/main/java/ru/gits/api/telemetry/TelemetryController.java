package ru.gits.api.telemetry;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

import ru.gits.api.security.CurrentUser;

/**
 * Telemetry batches of the candidate's task: JSON with the CSRF header every 2 seconds, and the same batch as
 * text/plain from navigator.sendBeacon when the page is hidden or closed (see {@link TelemetryBeaconRequest}).
 */
@RestController
@RequestMapping("/candidate/tasks/{sessionTaskId}/telemetry")
class TelemetryController {

    private final TelemetryService telemetry;
    private final TelemetryProperties properties;

    TelemetryController(TelemetryService telemetry, TelemetryProperties properties) {
        this.telemetry = telemetry;
        this.properties = properties;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    TelemetryService.Accepted batch(@PathVariable UUID sessionTaskId, HttpServletRequest request)
            throws IOException {
        return telemetry.accept(inviteId(), sessionTaskId, body(request), false);
    }

    @PostMapping(consumes = MediaType.TEXT_PLAIN_VALUE)
    TelemetryService.Accepted beacon(@PathVariable UUID sessionTaskId, HttpServletRequest request)
            throws IOException {
        return telemetry.accept(inviteId(), sessionTaskId, body(request), true);
    }

    /** Reads at most one byte over the limit, so an oversized body is never loaded whole. */
    private byte[] body(HttpServletRequest request) throws IOException {
        int limit = properties.maxBatchBytes();
        if (request.getContentLengthLong() > limit) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Пакет больше " + limit / 1024 + " КБ");
        }
        try (InputStream in = request.getInputStream()) {
            return in.readNBytes(limit + 1);
        }
    }

    private static UUID inviteId() {
        return CurrentUser.candidate().inviteId();
    }
}
