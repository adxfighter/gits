package ru.gits.api.invite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import ru.gits.api.security.Hashing;
import ru.gits.api.support.ApiTest;
import ru.gits.core.invite.InviteRepository;

class InviteIntegrationTest extends ApiTest {

    @Autowired private InviteRepository invites;

    @Test
    void createsInviteLinkAndStoresOnlyTheTokenHash() throws Exception {
        var employer = login(accounts.employer());

        var response = mvc.perform(post("/invites").session(employer).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("candidateLabel", "  Анна Смирнова  ", "targetLevel", "SENIOR")))
                .andExpect(status().isCreated())
                .andReturn().getResponse();

        String link = read(response).get("link").asText();
        assertThat(link).startsWith("http://localhost:8080/c/");
        String token = link.substring(link.lastIndexOf('/') + 1);
        assertThat(token).hasSize(43);

        var stored = invites.findByTokenHash(Hashing.sha256Hex(token)).orElseThrow();
        assertThat(stored.getTokenHash()).isNotEqualTo(token);
        assertThat(stored.getCandidateLabel()).isEqualTo("Анна Смирнова");
    }

    @Test
    void employersSeeOnlyTheirCompanyInvites() throws Exception {
        var alice = login(accounts.employer());
        var bob = login(accounts.employer());
        createInviteToken(alice);
        createInviteToken(alice);
        createInviteToken(bob);

        mvc.perform(get("/invites").session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("CREATED"))
                .andExpect(jsonPath("$[0].tokenHash").doesNotExist());
        mvc.perform(get("/invites").session(bob)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void rejectsInvalidInviteRequest() throws Exception {
        var employer = login(accounts.employer());

        mvc.perform(post("/invites").session(employer).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("candidateLabel", " ", "targetLevel", "MIDDLE")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/invites").session(employer).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("candidateLabel", "Кандидат", "targetLevel", "GURU")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void candidateCookieDoesNotGrantEmployerAccess() throws Exception {
        String token = createInviteToken(login(accounts.employer()));
        var entered = mvc.perform(post("/candidate/enter").with(csrf()).with(client()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("token", token)))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        mvc.perform(get("/invites").cookie(candidateCookie(entered))).andExpect(status().isUnauthorized());
    }
}
