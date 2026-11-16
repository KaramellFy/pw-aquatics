package com.pwaquatics.shop.coupon.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** Coupon codes: 3–32 letters, digits, hyphens or underscores, compared without regard to case. */
public final class CouponCode {

    /** What an owner may choose as a code (after {@link #normalise}). */
    public static final Pattern FORMAT = Pattern.compile("[A-Z0-9][A-Z0-9_-]{2,31}");

    private CouponCode() {}

    /** Trimmed and in upper case, as codes are stored and compared. */
    public static String normalise(String code) {
        return code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
    }

    public static boolean isValid(String normalised) {
        return FORMAT.matcher(normalised).matches();
    }
}
