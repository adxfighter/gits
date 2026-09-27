package ru.gits.api.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates candidate requests from the signed cookie. Created by {@code SecurityConfig}, not as a
 * bean, so that Spring Boot does not also register it as a global servlet filter.
 */
public class CandidateCookieFilter extends OncePerRequestFilter {

    public static final String ROLE_CANDIDATE = "ROLE_CANDIDATE";
    public static final String CONSENT_GIVEN = "CONSENT_GIVEN";

    private final CandidateCookieService cookies;

    public CandidateCookieFilter(CandidateCookieService cookies) {
        this.cookies = cookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        cookies.read(request).ifPresent(principal -> {
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
