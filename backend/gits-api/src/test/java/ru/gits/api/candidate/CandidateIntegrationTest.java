package ru.gits.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.servlet.http.Cookie;

import ru.gits.api.security.CandidateCookieService;
import ru.gits.api.security.Hashing;
import ru.gits.api.support.ApiTest;
import ru.gits.core.account.AppUserRepository;
import ru.gits.core.account.CompanyRepository;
import ru.gits.core.common.Level;
import ru.gits.core.invite.ConsentRepository;
import ru.gits.core.invite.Invite;
import ru.gits.core.invite.InviteRepository;
import ru.gits.core.invite.InviteStatus;

@ExtendWith(OutputCaptureExtension.class)
class CandidateIntegrationTest extends ApiTest {

    @Autowired private InviteRepository invites;
    @Autowired private ConsentRepository consents;
    @Autowired private CompanyRepository companies;
    @Autowired private AppUserRepository users;
    @Autowired private TransactionTemplate tx;

    @Test
    void candidateEntersAndMustAcceptConsentBeforeAnythingElse() throws Exception {
        String token = createInviteToken(login(accounts.employer()));

        Cookie cookie = candidateCookie(enter(token).andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateLabel").value("Иван Петров"))
                .andExpect(jsonPath("$.consentGiven").value(false))
                .andReturn().getResponse());

        mvc.perform(get("/candidate/me").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/candidate/consent").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.containsString("Сами символы")));
        // Everything else under /candidate requires consent
        mvc.perform(get("/candidate/session").cookie(cookie)).andExpect(status().isForbidden());

        MockHttpServletResponse accepted = mvc.perform(post("/candidate/consent").cookie(cookie).with(csrf()))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();
        Cookie withConsent = candidateCookie(accepted);

        // Past the security layer now: no session has been started yet
        mvc.perform(get("/candidate/session").cookie(withConsent)).andExpect(status().isNotFound());
        UUIDHolder invite = inviteFor(token);
        assertThat(consents.existsByInviteIdAndVersion(invite.id(), 1)).isTrue();
    }

    @Test
    void reEnteringAfterConsentKeepsConsent() throws Exception {
        String token = createInviteToken(login(accounts.employer()));
        Cookie cookie = candidateCookie(enter(token).andReturn().getResponse());
        mvc.perform(post("/candidate/consent").cookie(cookie).with(csrf())).andExpect(status().isNoContent());

        enter(token).andExpect(status().isOk()).andExpect(jsonPath("$.consentGiven").value(true));
    }

    @Test
    void firstEntryMarksInviteStartedAndKeepsFirstEntryTime() throws Exception {
        String token = createInviteToken(login(accounts.employer()));

        enter(token).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("STARTED"));
        Instant firstEntry = invites.findByTokenHash(Hashing.sha256Hex(token)).orElseThrow().getUsedAt();
        enter(token).andExpect(status().isOk());

        var invite = invites.findByTokenHash(Hashing.sha256Hex(token)).orElseThrow();
        assertThat(invite.getStatus()).isEqualTo(InviteStatus.STARTED);
        assertThat(firstEntry).isNotNull();
        assertThat(invite.getUsedAt()).isEqualTo(firstEntry);
    }

    @Test
    void revokingInviteEndsAccessForIssuedCookies() throws Exception {
        String token = createInviteToken(login(accounts.employer()));
        Cookie cookie = candidateCookie(enter(token).andReturn().getResponse());
        mvc.perform(get("/candidate/me").cookie(cookie)).andExpect(status().isOk());

        tx.executeWithoutResult(s -> invites.findByTokenHash(Hashing.sha256Hex(token)).orElseThrow().revoke());

        mvc.perform(get("/candidate/me").cookie(cookie)).andExpect(status().isUnauthorized());
        enter(token).andExpect(status().isGone());
    }

    @Test
    void unknownTokenIsNotFound() throws Exception {
        enter(Hashing.newToken()).andExpect(status().isNotFound());
    }

    @Test
    void completedInviteCannotBeReused() throws Exception {
        String token = createInviteToken(login(accounts.employer()));
        tx.executeWithoutResult(s -> invites.findByTokenHash(Hashing.sha256Hex(token)).orElseThrow().markCompleted());

        enter(token).andExpect(status().isGone())
                .andExpect(jsonPath("$.detail").value("Оценка по этой ссылке уже пройдена"));
    }

    @Test
    void expiredInviteIsGoneAndMarkedExpired() throws Exception {
        var account = accounts.employer();
        String token = Hashing.newToken();
        tx.executeWithoutResult(s -> invites.save(new Invite(companies.getReferenceById(account.companyId()),
                users.getReferenceById(account.userId()), "Опоздавший", Level.JUNIOR, Hashing.sha256Hex(token),
                Instant.now().minusSeconds(60), Instant.now().minusSeconds(3600))));

        enter(token).andExpect(status().isGone());

        assertThat(invites.findByTokenHash(Hashing.sha256Hex(token)).orElseThrow().getStatus())
                .isEqualTo(InviteStatus.EXPIRED);
    }

    @Test
    void tamperedCookieIsRejected() throws Exception {
        String token = createInviteToken(login(accounts.employer()));
        Cookie cookie = candidateCookie(enter(token).andReturn().getResponse());
        String value = cookie.getValue();
        String forged = value.substring(0, value.indexOf('.')) + ".AAAA" + value.substring(value.indexOf('.') + 5);

        mvc.perform(get("/candidate/me").cookie(new Cookie(CandidateCookieService.COOKIE_NAME, forged)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inviteTokensAreNotLogged(CapturedOutput output) throws Exception {
        String token = createInviteToken(login(accounts.employer()));
        enter(token);

        assertThat(output.getAll()).doesNotContain(token);
    }

    private org.springframework.test.web.servlet.ResultActions enter(String token) throws Exception {
        return mvc.perform(post("/candidate/enter").with(csrf()).with(client()).contentType(MediaType.APPLICATION_JSON)
                .content(body("token", token)));
    }

    private record UUIDHolder(java.util.UUID id) {
    }

    private UUIDHolder inviteFor(String token) {
        return new UUIDHolder(invites.findByTokenHash(Hashing.sha256Hex(token)).orElseThrow().getId());
    }
}
