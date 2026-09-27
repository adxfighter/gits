package ru.gits.api.security;

import java.util.UUID;

/**
 * Candidate identity carried by the signed candidate cookie.
 *
 * @param inviteId       invite the candidate entered with
 * @param consentVersion accepted consent text version, 0 when not accepted yet
 */
public record CandidatePrincipal(UUID inviteId, int consentVersion) {

    public boolean hasConsent() {
        return consentVersion > 0;
    }

    public CandidatePrincipal withConsent(int version) {
        return new CandidatePrincipal(inviteId, version);
    }
}
