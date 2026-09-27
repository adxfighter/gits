package ru.gits.api.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import ru.gits.core.invite.InviteRepository;
import ru.gits.core.invite.InviteStatus;

/**
 * Authenticates candidate requests from the signed cookie. The invite is re-checked on every request,
 * so revoking or completing it ends access even for cookies issued earlier. Created by
 * {@code SecurityConfig}, not as a bean, so that Spring Boot does not also register it as a servlet filter.
 */
public class CandidateCookieFilter extends OncePerRequestFilter {

    public static final String ROLE_CANDIDATE = "ROLE_CANDIDATE";
    public static final String CONSENT_GIVEN = "CONSENT_GIVEN";

    private static final Set<InviteStatus> ACTIVE = EnumSet.of(InviteStatus.CREATED, InviteStatus.STARTED);

    private final CandidateCookieService cookies;
    private final InviteRepository invites;

    public CandidateCookieFilter(CandidateCookieService cookies, InviteRepository invites) {
        this.cookies = cookies;
        this.invites = invites;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        cookies.read(request)
                .filter(principal -> invites.findStatusById(principal.inviteId()).map(ACTIVE::contains).orElse(false))
                .ifPresent(principal -> {
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(new CandidateAuthentication(principal));
                    SecurityContextHolder.setContext(context);
                });
        chain.doFilter(request, response);
    }

    static final class CandidateAuthentication extends AbstractAuthenticationToken {

        private final transient CandidatePrincipal principal;

        CandidateAuthentication(CandidatePrincipal principal) {
            super(authorities(principal));
            this.principal = principal;
            setAuthenticated(true);
        }

        private static List<GrantedAuthority> authorities(CandidatePrincipal principal) {
            List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority(ROLE_CANDIDATE));
            if (principal.hasConsent()) {
                authorities.add(new SimpleGrantedAuthority(CONSENT_GIVEN));
            }
            return authorities;
        }

        @Override
        public Object getCredentials() {
            return "";
        }

        @Override
        public CandidatePrincipal getPrincipal() {
            return principal;
        }
    }
}
