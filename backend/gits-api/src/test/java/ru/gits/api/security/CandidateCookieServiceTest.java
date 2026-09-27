package ru.gits.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import ru.gits.api.config.GitsProperties;

class CandidateCookieServiceTest {

    private static final Instant ISSUED = Instant.parse("2026-09-27T10:00:00Z");
    private static final Duration TTL = Duration.ofHours(12);

    private static CandidateCookieService serviceAt(Instant now) {
        var security = new GitsProperties.Security("unit-test-secret", TTL, false, 10, 20);
        var properties = new GitsProperties("http://localhost:8080", Duration.ofDays(7), security, null);
        return new CandidateCookieService(properties, Clock.fixed(now, ZoneOffset.UTC), "/api");
    }

    private static String issueCookie() {
        var response = new MockHttpServletResponse();
        serviceAt(ISSUED).write(response, new CandidatePrincipal(UUID.randomUUID(), 1));
        String header = response.getHeader("Set-Cookie");
        assertThat(header).contains("Path=/api/candidate").contains("HttpOnly").contains("SameSite=Lax");
        return header.substring(CandidateCookieService.COOKIE_NAME.length() + 1, header.indexOf(';'));
    }

    @Test
    void validUntilJustBeforeExpiry() {
        String value = issueCookie();

        assertThat(serviceAt(ISSUED.plus(TTL).minusSeconds(1)).parse(value)).isPresent()
                .get().satisfies(p -> assertThat(p.hasConsent()).isTrue());
    }

    @Test
    void rejectedAtExpirySecond() {
        String value = issueCookie();

        assertThat(serviceAt(ISSUED.plus(TTL)).parse(value)).isEmpty();
    }

    @Test
    void rejectedWhenSignedWithAnotherKey() {
        String value = issueCookie();
        var otherSecurity = new GitsProperties.Security("another-secret", TTL, false, 10, 20);
        var other = new CandidateCookieService(new GitsProperties("x", Duration.ofDays(7), otherSecurity, null),
                Clock.fixed(ISSUED, ZoneOffset.UTC), "/api");

        assertThat(other.parse(value)).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        var service = serviceAt(ISSUED);

        assertThat(service.parse("")).isEmpty();
        assertThat(service.parse("no-dot")).isEmpty();
        assertThat(service.parse("!!!.???")).isEmpty();
    }
}
