package com.pwaquatics.shop.identity.api;

import com.pwaquatics.shop.identity.application.view.CurrentUserView;
import com.pwaquatics.shop.identity.domain.UserRole;
import jakarta.annotation.Nullable;

/**
 * Response of {@code GET /auth/me} and the sign-in endpoints.
 *
 * @param twoFactorRequired the password was right; signing in finishes with a code from the
 *     authenticator app ({@code POST /auth/login/two-factor})
 * @param twoFactorSetupRequired a signed-in administrator who must set up two-step sign-in before
 *     the admin area opens
 */
public record SessionView(
        boolean authenticated,
        @Nullable CurrentUserView user,
        boolean twoFactorRequired,
        boolean twoFactorSetupRequired) {

    static SessionView anonymous() {
        return new SessionView(false, null, false, false);
    }

    static SessionView secondStepRequired() {
        return new SessionView(false, null, true, false);
    }

    /** @param adminAccess the session passed both steps (see {@code SessionManager}) */
    static SessionView of(CurrentUserView user, boolean adminAccess) {
        return new SessionView(true, user, false, user.role() == UserRole.ADMIN && !adminAccess);
    }
}
