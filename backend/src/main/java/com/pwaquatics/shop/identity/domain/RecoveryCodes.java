package com.pwaquatics.shop.identity.domain;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * One-time recovery codes: what an administrator types instead of the app's code when the phone
 * is lost. Each has 60 random bits ({@code xxxx-xxxx-xxxx}, Base32 letters and digits); only a
 * SHA-256 hash of it is stored.
 */
public final class RecoveryCodes {

    public static final int COUNT = 10;
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz234567";
    private static final int LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private RecoveryCodes() {}

    /** {@link #COUNT} new codes, formatted for reading. */
    public static List<String> generate() {
        return IntStream.range(0, COUNT).mapToObj(i -> newCode()).toList();
    }

    /** What is compared and hashed: lower case without spaces and dashes. */
    public static String normalise(String typed) {
        return typed.replaceAll("[\\s-]", "").toLowerCase(Locale.ROOT);
    }

    /** Whether a normalised text has the shape of a recovery code. */
    public static boolean isCodeShaped(String normalised) {
        return normalised.length() == LENGTH && normalised.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0);
    }

    private static String newCode() {
        StringBuilder code = new StringBuilder(LENGTH + 2);
        for (int i = 0; i < LENGTH; i++) {
            if (i > 0 && i % 4 == 0) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
