package com.pwaquatics.shop.security;

import java.io.Serializable;
import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * The logged-in user as stored in the session. Deliberately minimal: only the id and role.
 * Everything else is loaded fresh from the database when needed.
 *
 * <p>{@link #getName()} is the user id, which Spring Session indexes so that all sessions
 * of a user can be found and ended (password change, account deletion, lock).
 */
public record ShopUserPrincipal(UUID userId, String role) implements AuthenticatedPrincipal, Serializable {

    @Override
    public String getName() {
        return userId.toString();
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
