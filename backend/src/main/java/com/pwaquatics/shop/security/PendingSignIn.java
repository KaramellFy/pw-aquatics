package com.pwaquatics.shop.security;

import java.io.Serializable;
import java.time.Instant;

/**
 * A sign-in whose password was right and that waits for the code of the second step. Kept in the
 * (not yet signed-in) session; it grants nothing by itself.
 *
 * @param attemptsLeft wrong codes allowed before the password must be entered again
 */
record PendingSignIn(ShopUserPrincipal principal, Instant expiresAt, int attemptsLeft) implements Serializable {}
