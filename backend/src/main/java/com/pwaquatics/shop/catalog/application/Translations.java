package com.pwaquatics.shop.catalog.application;

import java.util.Arrays;
import java.util.List;

/**
 * Which languages a page has its own text in. English always; German once the main German text
 * is written. The shop keeps a German page that only shows English text out of search engines
 * and names only these languages as alternates (docs/adr/012-language-in-links.md).
 */
final class Translations {

    private Translations() {}

    /** @param germanTexts the page's main German texts; any one written makes the page German too */
    static List<String> of(String... germanTexts) {
        boolean german = Arrays.stream(germanTexts).anyMatch(text -> text != null && !text.isBlank());
        return german ? List.of("en", "de") : List.of("en");
    }
}
