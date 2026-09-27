package ru.gits.api.candidate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ru.gits.api.security.CandidatePrincipal;
import ru.gits.api.security.Hashing;
import ru.gits.core.common.Level;
import ru.gits.core.invite.Consent;
import ru.gits.core.invite.ConsentRepository;
import ru.gits.core.invite.Invite;
import ru.gits.core.invite.InviteRepository;
import ru.gits.core.invite.InviteStatus;

@Service
public class CandidateService {

    public record CandidateView(String candidateLabel, Level targetLevel, InviteStatus status, boolean consentGiven) {
    }

    public record ConsentView(int version, String text, boolean accepted) {
    }

    private final InviteRepository invites;
    private final ConsentRepository consents;
    private final ConsentText consentText;
    private final Clock clock;

    CandidateService(InviteRepository invites, ConsentRepository consents, ConsentText consentText, Clock clock) {
        this.invites = invites;
        this.consents = consents;
        this.consentText = consentText;
        this.clock = clock;
    }

    /** Validates the invite link token and returns the principal to store in the candidate cookie. */
    // An expired invite is marked EXPIRED even though the request fails with 410
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public CandidatePrincipal enter(String token) {
        Invite invite = invites.findByTokenHash(Hashing.sha256Hex(token))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ссылка недействительна"));
        switch (invite.getStatus()) {
            case COMPLETED -> throw gone("Оценка по этой ссылке уже пройдена");
            case REVOKED -> throw gone("Приглашение отозвано работодателем");
            case EXPIRED -> throw gone("Срок действия ссылки истёк");
            case CREATED -> {
                if (invite.isExpired(clock.instant())) {
                    invite.markExpired();
                    throw gone("Срок действия ссылки истёк");
                }
                // First entry: the employer sees that the link was opened. Re-entry stays possible
                // (page reload, another device) until the assessment is completed.
                invite.markStarted(clock.instant());
            }
            case STARTED -> {
                // already opened before; nothing to update
            }
        }
        boolean consented = consents.existsByInviteIdAndVersion(invite.getId(), ConsentText.CURRENT_VERSION);
        return new CandidatePrincipal(invite.getId(), consented ? ConsentText.CURRENT_VERSION : 0);
    }

    @Transactional(readOnly = true)
    public CandidateView view(UUID inviteId) {
        Invite invite = invite(inviteId);
        return new CandidateView(invite.getCandidateLabel(), invite.getTargetLevel(), invite.getStatus(),
                consents.existsByInviteIdAndVersion(inviteId, ConsentText.CURRENT_VERSION));
    }

    @Transactional(readOnly = true)
    public ConsentView consent(UUID inviteId) {
        return new ConsentView(ConsentText.CURRENT_VERSION, consentText.markdown(),
                consents.existsByInviteIdAndVersion(inviteId, ConsentText.CURRENT_VERSION));
    }

    /** Records acceptance of the current consent version; repeated calls are no-ops. */
    @Transactional
    public int acceptConsent(UUID inviteId, String clientIp, String userAgent) {
        if (!consents.existsByInviteIdAndVersion(inviteId, ConsentText.CURRENT_VERSION)) {
            Instant now = clock.instant();
            String agent = userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 500));
            consents.save(new Consent(invite(inviteId), ConsentText.CURRENT_VERSION, now,
                    Hashing.sha256Hex(clientIp), agent));
        }
        return ConsentText.CURRENT_VERSION;
    }

    private Invite invite(UUID inviteId) {
        return invites.findById(inviteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Приглашение не найдено"));
    }

    private static ResponseStatusException gone(String reason) {
        return new ResponseStatusException(HttpStatus.GONE, reason);
    }
}
