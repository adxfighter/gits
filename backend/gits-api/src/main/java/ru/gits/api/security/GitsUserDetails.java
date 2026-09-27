package ru.gits.api.security;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import ru.gits.core.account.AppUser;
import ru.gits.core.account.UserRole;

/** Authenticated employer or admin; stored in the HTTP session, so it keeps only plain values. */
public final class GitsUserDetails implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final UUID companyId;
    private final String companyName;
    private final String email;
    private final String passwordHash;
    private final UserRole role;
    private final boolean enabled;

    GitsUserDetails(AppUser user) {
        this.userId = user.getId();
        this.companyId = user.getCompany() == null ? null : user.getCompany().getId();
        this.companyName = user.getCompany() == null ? null : user.getCompany().getName();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.enabled = user.isEnabled();
    }

    public UUID userId() {
        return userId;
    }

    public UUID companyId() {
        return companyId;
    }

    public String companyName() {
        return companyName;
    }

    public UserRole role() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
