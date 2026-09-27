package ru.gits.api.auth;

import java.time.Duration;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import ru.gits.api.config.GitsProperties;
import ru.gits.api.security.CurrentUser;
import ru.gits.api.security.GitsUserDetails;
import ru.gits.api.security.RateLimiter;
import ru.gits.api.web.ClientIp;
import ru.gits.core.account.UserRole;

/** Employer and admin authentication for the SPA (JSON login backed by the HTTP session). */
@RestController
@RequestMapping("/auth")
class AuthController {

    record LoginRequest(@NotBlank @Email @Size(max = 320) String email,
                        @NotBlank @Size(max = 200) String password) {
    }

    record MeResponse(UUID userId, String email, UserRole role, UUID companyId, String companyName) {
        static MeResponse of(GitsUserDetails user) {
            return new MeResponse(user.userId(), user.getUsername(), user.role(), user.companyId(),
                    user.companyName());
        }
    }

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contexts;
    private final RateLimiter rateLimiter;
    private final int loginPerMinute;

    AuthController(AuthenticationManager authenticationManager, SecurityContextRepository contexts,
                   RateLimiter rateLimiter, GitsProperties properties) {
        this.authenticationManager = authenticationManager;
        this.contexts = contexts;
        this.rateLimiter = rateLimiter;
        this.loginPerMinute = properties.security().loginPerMinute();
    }

    /** Issues the XSRF-TOKEN cookie; the SPA calls it once before the first state-changing request. */
    @GetMapping("/csrf")
    ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    MeResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                     HttpServletResponse response) {
        rateLimiter.check("login", ClientIp.of(request), loginPerMinute, Duration.ofMinutes(1));
        try {
            var authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.email(), body.password()));
            // New session on login: prevents session fixation
            HttpSession previous = request.getSession(false);
            if (previous != null) {
                previous.invalidate();
            }
            request.getSession(true);
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contexts.saveContext(context, request, response);
            return MeResponse.of((GitsUserDetails) authentication.getPrincipal());
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный email или пароль");
        }
    }

    @GetMapping("/me")
    MeResponse me() {
        return MeResponse.of(CurrentUser.employer());
    }
}
