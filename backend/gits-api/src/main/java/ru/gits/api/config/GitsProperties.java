package ru.gits.api.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application settings bound from {@code gits.*}.
 *
 * @param publicBaseUrl base URL used to build candidate invite links
 * @param inviteTtl     how long an invite link stays valid
 * @param security      cookie and rate limit settings
 * @param demo          demo accounts created at startup when credentials are provided
 */
@ConfigurationProperties(prefix = "gits")
public record GitsProperties(String publicBaseUrl, Duration inviteTtl, Security security, Demo demo) {

    /**
     * @param candidateSecret HMAC key for candidate cookies; random per start when blank
     * @param candidateTtl    lifetime of the candidate cookie
     * @param secureCookies   set the Secure flag (enable behind HTTPS)
     * @param loginPerMinute  employer login attempts per IP per minute
     * @param enterPerMinute  candidate invite-link entries per IP per minute
     */
    public record Security(String candidateSecret, Duration candidateTtl, boolean secureCookies,
                           int loginPerMinute, int enterPerMinute) {
    }

    public record Demo(String employerEmail, String employerPassword, String adminEmail, String adminPassword,
                       String companyName) {
    }
}
