package com.saaketh.budget.auth;

import com.saaketh.budget.user.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The logged-in user as Spring Security sees it. Stored in the session after login, so
 * controllers can get the current user's id without another database lookup.
 */
public class AuthenticatedUser implements UserDetails, CredentialsContainer {

    private final Long id;
    private final String email;
    private String passwordHash;

    public AuthenticatedUser(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
    }

    public Long getId() {
        return id;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** Called by Spring Security after login so the hash isn't kept in the session. */
    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    /** No roles yet; every user has the same permissions. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }
}
