package ru.gits.api.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import ru.gits.api.config.GitsProperties;

/**
 * Stateless, HMAC-signed candidate cookie. It is separate from the employer session cookie so that an
 * employer and a candidate can use the same browser at the same time during a demo.
 * Format: base64url("inviteId:consentVersion:expiresEpochSecond") + "." + base64url(HMAC-SHA256).
 */
@Component
public class CandidateCookieService {

    public static final String COOKIE_NAME = "GITS_CANDIDATE";
    private static final Logger log = LoggerFactory.getLogger(CandidateCookieService.class);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final String cookiePath;
    private final GitsProperties.Security settings;
    private final Clock clock;

    public CandidateCookieService(GitsProperties properties, Clock clock,
                                  @Value("${server.servlet.context-path:}") String contextPath) {
        this.cookiePath = contextPath + "/candidate";
        this.settings = properties.security();
        this.clock = clock;
        String configured = settings.candidateSecret();
        if (StringUtils.hasText(configured)) {
            this.secret = configured.getBytes(StandardCharsets.UTF_8);
        } else {
            log.warn("GITS_CANDIDATE_SECRET is not set: using a random key, candidate sessions end on restart");
            this.secret = Hashing.newToken().getBytes(StandardCharsets.UTF_8);
        }
    }

    public void write(HttpServletResponse response, CandidatePrincipal principal) {
        long expires = clock.instant().plus(settings.candidateTtl()).getEpochSecond();
        String payload = principal.inviteId() + ":" + principal.consentVersion() + ":" + expires;
        String encoded = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String value = encoded + "." + ENCODER.encodeToString(sign(encoded));
        response.addHeader("Set-Cookie", cookie(value, settings.candidateTtl().toSeconds()).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader("Set-Cookie", cookie("", 0).toString());
    }

    public Optional<CandidatePrincipal> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return parse(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    Optional<CandidatePrincipal> parse(String value) {
        try {
            int dot = value.indexOf('.');
            if (dot <= 0) {
                return Optional.empty();
            }
            String encoded = value.substring(0, dot);
            byte[] signature = DECODER.decode(value.substring(dot + 1));
            if (!MessageDigest.isEqual(signature, sign(encoded))) {
                return Optional.empty();
            }
            String[] parts = new String(DECODER.decode(encoded), StandardCharsets.UTF_8).split(":");
            if (parts.length != 3 || !Instant.ofEpochSecond(Long.parseLong(parts[2])).isAfter(clock.instant())) {
                return Optional.empty();
            }
            return Optional.of(new CandidatePrincipal(UUID.fromString(parts[0]), Integer.parseInt(parts[1])));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private ResponseCookie cookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(settings.secureCookies())
                .sameSite("Lax")
                .path(cookiePath)
                .maxAge(maxAgeSeconds)
                .build();
    }

    private byte[] sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is always available", e);
        }
    }
}
