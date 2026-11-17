package com.pwaquatics.shop.identity.application;

import jakarta.annotation.Nullable;

/**
 * What Google's ID token says about the person signing in, after the token's signature, issuer,
 * audience, expiry and nonce have been checked.
 *
 * @param subject Google's stable, unique id for the account ({@code sub})
 * @param emailVerified whether Google has verified that the account owns {@code email}
 */
public record GoogleAccount(
        String subject,
        String email,
        boolean emailVerified,
        @Nullable String givenName,
        @Nullable String familyName,
        @Nullable String name) {}
