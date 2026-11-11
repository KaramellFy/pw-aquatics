package com.pwaquatics.shop.common.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** URL slugs such as {@code blue-dream-high}: lower-case ASCII letters, digits and single hyphens. */
public final class Slugs {

    /** What a valid slug looks like. */
    public static final String PATTERN = "[a-z0-9]+(-[a-z0-9]+)*";

    private static final Pattern VALID = Pattern.compile(PATTERN);
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern SEPARATORS = Pattern.compile("[^a-z0-9]+");

    private Slugs() {}

    /**
     * A slug from a name: "Größe & Farbe" → "groesse-farbe". German umlauts are spelled out,
     * other accents dropped. Returns an empty string when nothing usable is left.
     */
    public static String of(String text, int maxLength) {
        String lower = text.toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        String ascii = DIACRITICS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
        String slug = SEPARATORS.matcher(ascii).replaceAll("-").replaceAll("^-+|-+$", "");
        if (slug.length() > maxLength) {
            slug = slug.substring(0, maxLength).replaceAll("-+$", "");
        }
        return slug;
    }

    public static boolean isValid(String slug) {
        return slug != null && VALID.matcher(slug).matches();
    }
}
