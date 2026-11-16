package com.pwaquatics.shop.common.text;

import java.util.Locale;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * The language of shop texts in a response (docs/adr/012-language-in-links.md).
 *
 * <p>Texts the owner writes (product names, descriptions, …) are stored in English and, where
 * written, in German ({@code <column>_de}). A response is in the language of the request's
 * {@code Accept-Language} header, which the shop sends with every API call; anything other
 * than German gives English. A German text that is empty falls back to the English one.
 */
public enum ContentLanguage {
    EN,
    DE;

    /** The language of the current request (English outside a request, e.g. in jobs). */
    public static ContentLanguage current() {
        return of(LocaleContextHolder.getLocale());
    }

    /** German for a German locale, English for anything else (e.g. a customer's stored locale). */
    public static ContentLanguage of(Locale locale) {
        return locale != null && "de".equals(locale.getLanguage()) ? DE : EN;
    }

    /** The text in this language: the German one if it is written, otherwise the English one. */
    public String pick(String english, String german) {
        return this == DE && german != null && !german.isBlank() ? german : english;
    }
}
