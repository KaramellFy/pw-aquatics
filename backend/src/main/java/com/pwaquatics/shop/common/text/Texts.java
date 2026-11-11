package com.pwaquatics.shop.common.text;

/** Small helpers for text coming from forms. */
public final class Texts {

    private Texts() {}

    /** Trims the text; an empty or blank text becomes null (optional fields). */
    public static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
