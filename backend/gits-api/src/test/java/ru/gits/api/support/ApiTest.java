package ru.gits.api.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.concurrent.ThreadLocalRandom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

import ru.gits.api.security.CandidateCookieService;

/** Base class for HTTP-level tests against a real PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class ApiTest {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected TestAccounts accounts;

    protected MockHttpSession login(TestAccounts.Account account) throws Exception {
        return (MockHttpSession) mvc.perform(post("/auth/login").with(csrf()).with(client())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("email", account.email(), "password", TestAccounts.PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    /**
     * A fresh client address per call: rate limits are per address, and MockMvc requests would otherwise
     * all share 127.0.0.1 across the whole test suite.
     */
    protected static RequestPostProcessor client() {
        var random = ThreadLocalRandom.current();
        String address = "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    /** Invite link token created through the API by {@code employer}. */
    protected String createInviteToken(MockHttpSession employer) throws Exception {
        var response = mvc.perform(post("/invites").session(employer).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("candidateLabel", "Иван Петров", "targetLevel", "MIDDLE")))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
        String link = read(response).get("link").asText();
        return link.substring(link.lastIndexOf("/c/") + 3);
    }

    /** Extracts the candidate cookie from a Set-Cookie header written by the API. */
    protected static Cookie candidateCookie(MockHttpServletResponse response) {
        return response.getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith(CandidateCookieService.COOKIE_NAME + "="))
                .reduce((first, second) -> second)
                .map(h -> new Cookie(CandidateCookieService.COOKIE_NAME,
                        h.substring(CandidateCookieService.COOKIE_NAME.length() + 1, h.indexOf(';'))))
                .orElseThrow(() -> new AssertionError("No candidate cookie in response"));
    }

    protected JsonNode read(MockHttpServletResponse response) throws Exception {
        return json.readTree(response.getContentAsString());
    }

    protected String body(String... keyValues) throws Exception {
        var node = json.createObjectNode();
        for (int i = 0; i < keyValues.length; i += 2) {
            node.put(keyValues[i], keyValues[i + 1]);
        }
        return json.writeValueAsString(node);
    }
}
