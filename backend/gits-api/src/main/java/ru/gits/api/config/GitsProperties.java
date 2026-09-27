package ru.gits.api.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application settings bound from {@code gits.*}.
 *
 * @param publicBaseUrl base URL used to build candidate invite links
 * @param inviteTtl     how long an invite link stays valid
 * @param security      cookie and rate limit settings
 * @param demo          demo accounts created at startup when credentials are provided
 * @param taskbank      task bank loaded into the database at startup
 */
@ConfigurationProperties(prefix = "gits")
public record GitsProperties(String publicBaseUrl, Duration inviteTtl, Security security, Demo demo,
                             Taskbank taskbank) {

    /**
     * @param path              task bank directory (the one with schema/ and java/); loading is off when blank
     * @param excludedTemplates templates never offered to candidates (the format example T00)
     */
    public record Taskbank(String path, List<String> excludedTemplates) {

        public Taskbank {
            excludedTemplates = excludedTemplates == null ? List.of() : List.copyOf(excludedTemplates);
        }
    }

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
