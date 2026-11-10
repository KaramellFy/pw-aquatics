package com.pwaquatics.shop.identity.domain;

import java.util.Locale;

/** Email normalisation: addresses are always stored and compared trimmed and lower-case. */
public final class EmailAddress {

    private EmailAddress() {}

    public static String normalise(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
