package ru.gits.api.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Typed access to the authenticated principal of the current request. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static GitsUserDetails employer() {
        return user();
    }

    /** The signed-in employer or administrator. */
    public static GitsUserDetails user() {
        return (GitsUserDetails) authentication().getPrincipal();
    }

    public static CandidatePrincipal candidate() {
        return (CandidatePrincipal) authentication().getPrincipal();
    }

    private static Authentication authentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new IllegalStateException("No authenticated principal");
        }
        return authentication;
    }
}
