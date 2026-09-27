package ru.gits.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import ru.gits.api.support.ApiTest;
import ru.gits.api.support.TestAccounts;

@ExtendWith(OutputCaptureExtension.class)
class AuthIntegrationTest extends ApiTest {

    @Autowired private CookieCsrfTokenRepository csrfRepository;

    @Test
    void employerLogsInAndReadsProfile() throws Exception {
        var account = accounts.employer();

        var session = login(account);

        mvc.perform(get("/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(account.email()))
                .andExpect(jsonPath("$.role").value("EMPLOYER"))
                .andExpect(jsonPath("$.companyId").value(account.companyId().toString()));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        var account = accounts.employer();

        mvc.perform(post("/auth/login").with(csrf()).with(client()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("email", account.email(), "password", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Неверный email или пароль"));
    }

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/invites")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithoutCsrfTokenIsForbidden() throws Exception {
        var account = accounts.employer();

        mvc.perform(post("/auth/login").with(client()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("email", account.email(), "password", TestAccounts.PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfEndpointIsPublic() throws Exception {
        mvc.perform(get("/auth/csrf")).andExpect(status().isNoContent());
    }

    /**
     * Checked on the repository bean: spring-security-test's csrf() post-processor swaps the filter's
     * repository for the rest of the context, so an HTTP-level assertion would depend on test order.
     */
    @Test
    void csrfCookieIsReadableBySpaServedFromRoot() {
        var request = new MockHttpServletRequest();
        request.setContextPath("/api");
        var response = new MockHttpServletResponse();

        csrfRepository.saveToken(csrfRepository.generateToken(request), request, response);

        String header = response.getHeader("Set-Cookie");
        assertThat(header).startsWith("XSRF-TOKEN=").containsPattern("Path=/(;|$)")
                .doesNotContainIgnoringCase("HttpOnly");
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        var session = login(accounts.employer());

        mvc.perform(post("/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());

        mvc.perform(get("/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginIsRateLimitedPerClientAddress() throws Exception {
        var account = accounts.employer();
        String wrong = body("email", account.email(), "password", "wrong-password");

        for (int attempt = 0; attempt < 10; attempt++) {
            mvc.perform(post("/auth/login").with(csrf()).with(r -> { r.setRemoteAddr("10.77.0.1"); return r; })
                            .contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/auth/login").with(csrf()).with(r -> { r.setRemoteAddr("10.77.0.1"); return r; })
                        .contentType(MediaType.APPLICATION_JSON).content(wrong))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void passwordsAreNotLogged(CapturedOutput output) throws Exception {
        var account = accounts.employer();
        login(account);
        mvc.perform(post("/auth/login").with(csrf()).with(client()).contentType(MediaType.APPLICATION_JSON)
                .content(body("email", account.email(), "password", "another-secret-value")));

        assertThat(output.getAll()).doesNotContain(TestAccounts.PASSWORD).doesNotContain("another-secret-value");
    }
}
