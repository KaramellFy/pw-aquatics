package com.pwaquatics.shop.identity.application;

import com.pwaquatics.shop.common.error.BusinessRuleException;
import java.util.Locale;
import java.util.Set;

/**
 * Password rules beyond the length limits on the request DTOs: length matters most,
 * so there are no composition rules, only a ban on obvious choices.
 */
final class PasswordPolicy {

    static final int MIN_LENGTH = 10;
    static final int MAX_LENGTH = 128;

    private static final Set<String> COMMON = Set.of(
            "1234567890", "0123456789", "0987654321", "12345678910", "123456789a", "1q2w3e4r5t", "qwertyuiop",
            "qwertzuiop", "password123", "password1234", "passwort123", "iloveyou123", "1111111111", "aaaaaaaaaa");

    private PasswordPolicy() {}

    static void check(String password, String email) {
        String lower = password.toLowerCase(Locale.ROOT);
        boolean tooObvious = COMMON.contains(lower)
                || (email != null && lower.equals(email.toLowerCase(Locale.ROOT)))
                || lower.chars().distinct().count() < 4;
        if (tooObvious) {
            throw new BusinessRuleException(
                    "PASSWORD_TOO_WEAK", "This password is too easy to guess. Please choose a different one.");
        }
    }
}
