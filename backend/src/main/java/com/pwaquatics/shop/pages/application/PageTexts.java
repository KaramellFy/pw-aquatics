package com.pwaquatics.shop.pages.application;

import com.pwaquatics.shop.common.text.ContentLanguage;
import java.util.Locale;
import tools.jackson.databind.JsonNode;

/**
 * Interface texts of the server-filled pages (page titles, navigation, labels), generated from
 * the frontend's translations when the frontend is built ({@code pages.json}, "texts"), so both
 * show the same words. Owner-written texts come from the catalog, not from here.
 */
public final class PageTexts {

    private final JsonNode root;

    private PageTexts(JsonNode root) {
        this.root = root;
    }

    /** @throws IllegalArgumentException if there are no English texts */
    public static PageTexts of(JsonNode root) {
        if (root == null || !root.path("en").isObject()) {
            throw new IllegalArgumentException("pages.json has no English texts");
        }
        return new PageTexts(root);
    }

    /**
     * The text at a dotted key (e.g. "pages.home.title") in this language; English where the
     * language has none, an empty string where neither has it.
     */
    public String get(ContentLanguage language, String key) {
        String text = lookup(language.name().toLowerCase(Locale.ROOT), key);
        if (text == null && language != ContentLanguage.EN) {
            text = lookup("en", key);
        }
        return text == null ? "" : text;
    }

    /** {@link #get} with "{{name}}" placeholders filled in. */
    public String format(ContentLanguage language, String key, String name, String value) {
        return get(language, key).replace("{{" + name + "}}", value);
    }

    private String lookup(String language, String key) {
        JsonNode node = root.path(language);
        for (String part : key.split("\\.")) {
            node = node.path(part);
        }
        return node.isString() ? node.asString() : null;
    }
}
