package ru.gits.api.telemetry;

import java.util.regex.Pattern;

import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.security.web.util.matcher.RequestMatcher;

import jakarta.servlet.http.HttpServletRequest;

/**
 * A navigator.sendBeacon telemetry batch: POST with a text/plain body to the telemetry endpoint. sendBeacon cannot
 * set the CSRF header, so SecurityConfig takes the CSRF check for exactly these requests from
 * {@link TelemetryService}, which requires the task's one-time beacon token in the body.
 */
public final class TelemetryBeaconRequest implements RequestMatcher {

    private static final Pattern PATH = Pattern.compile("^/candidate/tasks/[0-9a-fA-F-]{36}/telemetry$");

    @Override
    public boolean matches(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod()) || request.getContentType() == null) {
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return PATH.matcher(path).matches() && isTextPlain(request.getContentType());
    }

    static boolean isTextPlain(String contentType) {
        try {
            return MediaType.TEXT_PLAIN.equalsTypeAndSubtype(MediaType.parseMediaType(contentType));
        } catch (InvalidMediaTypeException e) {
            return false;
        }
    }
}
