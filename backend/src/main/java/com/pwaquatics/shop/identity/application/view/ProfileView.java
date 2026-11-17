package com.pwaquatics.shop.identity.application.view;

import jakarta.annotation.Nullable;
import java.time.Instant;

/**
 * The customer's own profile.
 *
 * @param hasPassword false for accounts that only sign in with Google
 * @param googleEmail the connected Google account's address, or null if none is connected
 * @param confirmedUntil until when this session may change the email address or delete the
 *     account without typing the password (after confirming with Google), or null
 */
public record ProfileView(
        String email,
        String firstName,
        String lastName,
        @Nullable String phone,
        boolean marketingOptIn,
        String locale,
        Instant memberSince,
        boolean hasPassword,
        @Nullable String googleEmail,
        @Nullable Instant confirmedUntil) {}
