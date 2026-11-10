package com.pwaquatics.shop.identity.api;

import com.pwaquatics.shop.identity.application.view.CurrentUserView;
import jakarta.annotation.Nullable;

/** Response of {@code GET /auth/me} and {@code POST /auth/login}. */
public record SessionView(boolean authenticated, @Nullable CurrentUserView user) {

    static SessionView anonymous() {
        return new SessionView(false, null);
    }

    static SessionView of(CurrentUserView user) {
        return new SessionView(true, user);
    }
}
