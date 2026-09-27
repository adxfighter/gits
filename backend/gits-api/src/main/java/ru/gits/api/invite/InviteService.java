package ru.gits.api.invite;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.gits.api.config.GitsProperties;
import ru.gits.api.security.GitsUserDetails;
import ru.gits.api.security.Hashing;
import ru.gits.core.account.AppUserRepository;
import ru.gits.core.account.CompanyRepository;
import ru.gits.core.audit.AuditLog;
import ru.gits.core.audit.AuditLogRepository;
import ru.gits.core.common.Level;
import ru.gits.core.invite.Invite;
import ru.gits.core.invite.InviteRepository;
import ru.gits.core.invite.InviteStatus;

@Service
public class InviteService {

    public record CreatedInvite(UUID id, String link, Instant expiresAt) {
    }

    public record InviteView(UUID id, String candidateLabel, Level targetLevel, InviteStatus status,
                             Instant createdAt, Instant expiresAt, Instant usedAt) {
        static InviteView of(Invite invite) {
            return new InviteView(invite.getId(), invite.getCandidateLabel(), invite.getTargetLevel(),
                    invite.getStatus(), invite.getCreatedAt(), invite.getExpiresAt(), invite.getUsedAt());
        }
    }

    private final InviteRepository invites;
    private final CompanyRepository companies;
    private final AppUserRepository users;
    private final AuditLogRepository audit;
    private final GitsProperties properties;
    private final Clock clock;

    InviteService(InviteRepository invites, CompanyRepository companies, AppUserRepository users,
                  AuditLogRepository audit, GitsProperties properties, Clock clock) {
        this.invites = invites;
        this.companies = companies;
        this.users = users;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    /** Creates an invite; the raw token exists only in the returned link and is never stored. */
    @Transactional
    public CreatedInvite create(GitsUserDetails employer, String candidateLabel, Level targetLevel) {
        Instant now = clock.instant();
        String token = Hashing.newToken();
        Invite invite = invites.save(new Invite(
                companies.getReferenceById(employer.companyId()),
                users.getReferenceById(employer.userId()),
                candidateLabel.strip(), targetLevel, Hashing.sha256Hex(token),
                now.plus(properties.inviteTtl()), now));
        audit.save(new AuditLog(employer.getUsername(), "INVITE_CREATED", "invite", invite.getId(),
                "{\"targetLevel\":\"" + targetLevel + "\"}", now));
        return new CreatedInvite(invite.getId(), properties.publicBaseUrl() + "/c/" + token, invite.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public List<InviteView> list(UUID companyId) {
        return invites.findByCompanyIdOrderByCreatedAtDesc(companyId).stream().map(InviteView::of).toList();
    }
}
