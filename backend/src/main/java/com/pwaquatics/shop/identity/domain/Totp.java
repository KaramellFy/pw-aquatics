package com.pwaquatics.shop.identity.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Time-based one-time codes (TOTP, RFC 6238) as shown by authenticator apps: HMAC-SHA1, a new
 * six-digit code every 30 seconds.
 *
 * <p>A code is accepted for its own 30 seconds and one step either side (clocks drift), and only
 * once: the caller stores the step of the last accepted code and passes it back.
 */
public final class Totp {

    /** Length of a new key: 160 bits, as RFC 4226 recommends for HMAC-SHA1. */
    public static final int SECRET_BYTES = 20;
    public static final int DIGITS = 6;
    public static final int PERIOD_SECONDS = 30;
    private static final int WINDOW = 1;

    private Totp() {}

    /** The 30-second step a moment falls into. */
    public static long step(Instant at) {
        return Math.floorDiv(at.getEpochSecond(), PERIOD_SECONDS);
    }

    /** The code for one step, with leading zeros. */
    public static String code(byte[] secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return String.format("%0" + DIGITS + "d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Whether the text has the shape of a code (six digits). */
    public static boolean isCodeShaped(String text) {
        return text.length() == DIGITS && text.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    /**
     * The step of the matching code, if the code belongs to the current step or one either side
     * and is newer than the last accepted one.
     */
    public static OptionalLong match(byte[] secret, String code, Instant now, long lastUsedStep) {
        if (!isCodeShaped(code)) {
            return OptionalLong.empty();
        }
        byte[] given = code.getBytes(StandardCharsets.US_ASCII);
        long current = step(now);
        for (long step = current - WINDOW; step <= current + WINDOW; step++) {
            byte[] expected = code(secret, step).getBytes(StandardCharsets.US_ASCII);
            if (step > lastUsedStep && MessageDigest.isEqual(expected, given)) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }
}
