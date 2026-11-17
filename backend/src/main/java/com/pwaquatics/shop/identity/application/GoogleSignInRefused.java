package com.pwaquatics.shop.identity.application;

/**
 * Signing in with Google did not lead to a session. The web layer turns the reason into a message
 * on the page the customer returns to; nothing here says whether another account exists.
 */
public class GoogleSignInRefused extends RuntimeException {

    public enum Reason {
        /** Google has not verified the account's address, so it cannot be trusted as the shop login. */
        UNVERIFIED_EMAIL,
        /** The shop locked the account. */
        LOCKED,
        /** Administrators sign in with their password only. */
        PASSWORD_ONLY,
        /** The shop account is already connected to a different Google account. */
        OTHER_GOOGLE_ACCOUNT,
        /** Confirming with Google, but this Google account is not the one connected to the shop account. */
        NOT_CONNECTED
    }

    private final Reason reason;

    public GoogleSignInRefused(Reason reason) {
        super("Google sign-in refused: " + reason);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
