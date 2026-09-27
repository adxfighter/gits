package ru.gits.api.candidate;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import ru.gits.api.config.GitsProperties;
import ru.gits.api.security.CandidateCookieService;
import ru.gits.api.security.CandidatePrincipal;
import ru.gits.api.security.CurrentUser;
import ru.gits.api.security.RateLimiter;
import ru.gits.api.web.ClientIp;

@RestController
@RequestMapping("/candidate")
class CandidateController {

    record EnterRequest(@NotBlank @Size(max = 100) String token) {
    }

    private final CandidateService candidates;
    private final CandidateCookieService cookies;
    private final RateLimiter rateLimiter;
    private final int enterPerMinute;

    CandidateController(CandidateService candidates, CandidateCookieService cookies, RateLimiter rateLimiter,
                        GitsProperties properties) {
        this.candidates = candidates;
        this.cookies = cookies;
        this.rateLimiter = rateLimiter;
        this.enterPerMinute = properties.security().enterPerMinute();
    }

    @PostMapping("/enter")
    CandidateService.CandidateView enter(@Valid @RequestBody EnterRequest body, HttpServletRequest request,
                                         HttpServletResponse response) {
        rateLimiter.check("enter", ClientIp.of(request), enterPerMinute, Duration.ofMinutes(1));
        CandidatePrincipal principal = candidates.enter(body.token());
        cookies.write(response, principal);
        return candidates.view(principal.inviteId());
    }

    @GetMapping("/me")
    CandidateService.CandidateView me() {
        return candidates.view(CurrentUser.candidate().inviteId());
    }

    @GetMapping("/consent")
    CandidateService.ConsentView consent() {
        return candidates.consent(CurrentUser.candidate().inviteId());
    }

    @PostMapping("/consent")
    ResponseEntity<Void> acceptConsent(HttpServletRequest request, HttpServletResponse response) {
        CandidatePrincipal principal = CurrentUser.candidate();
        int version = candidates.acceptConsent(principal.inviteId(), ClientIp.of(request),
                request.getHeader(HttpHeaders.USER_AGENT));
        cookies.write(response, principal.withConsent(version));
        return ResponseEntity.noContent().build();
    }
}
