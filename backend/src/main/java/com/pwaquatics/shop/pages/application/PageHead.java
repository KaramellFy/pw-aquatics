package com.pwaquatics.shop.pages.application;

import jakarta.annotation.Nullable;
import java.util.List;

/**
 * What goes into a page's head; mirrors the frontend's {@code PageMeta}.
 *
 * @param path the page's link without language prefix and query (canonical link)
 * @param languages the languages the page's own texts are written in ("en", "de")
 * @param noIndex keep the page out of search engines (also when it is shown in a language its
 *     texts are not written in)
 * @param image a photo for link previews (a shop path or a full link)
 * @param structuredData schema.org data as JSON-LD
 */
record PageHead(
        String title,
        @Nullable String description,
        String path,
        List<String> languages,
        boolean noIndex,
        String type,
        @Nullable String image,
        @Nullable Object structuredData) {

    static final List<String> ALL_LANGUAGES = List.of("en", "de");

    static PageHead of(String title, @Nullable String description, String path) {
        return new PageHead(title, description, path, ALL_LANGUAGES, false, "website", null, null);
    }

    static PageHead notIndexed(String title) {
        return new PageHead(title, null, "/", ALL_LANGUAGES, true, "website", null, null);
    }
}
